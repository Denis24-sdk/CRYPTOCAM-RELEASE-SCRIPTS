import asyncio
import queue
import os
import re
import signal

import gnupg
from PyQt5.QtCore import (QDir, QEvent, QObject, Qt, QUrl, QVariant,
                          pyqtProperty, pyqtSignal, pyqtSlot)
from PyQt5.QtGui import QDrag, QDropEvent
from PyQt5.QtQuick import QQuickView
from toolz.curried import filter, groupby, map, pipe

from decrypt import create_ffmpeg
from file_list import *
from gpgsettings import GpgProvider

MAX_FFMPEG_JOBS = 3


class MainWindowController(QObject):
    listVisibilityChanged = pyqtSignal(bool)
    listItemsChanged = pyqtSignal("QVariant")
    statusTextChanged = pyqtSignal("QString")
    outputPathChanged = pyqtSignal("QString")

    file_list_items = []
    selected_item = None
    decrypt_tasks = {}
    ffmpeg_outputs = {}
    output_path = './'
    ffmpeg_queue: queue.Queue = queue.Queue()
    running_ffmpegs = 0

    def __init__(self, gpg_provider):
        super().__init__()
        self.gpg_provider = gpg_provider
        self.outputPathChanged.emit(self.output_path)

    @pyqtSlot(QVariant)
    def onFilesPicked(self, urls):
        # remove canceled, done and error items
        for file_item in list(self.file_list_items):
            if file_item.processingState in [FileListItem.State.Done,
                                             FileListItem.State.Error, FileListItem.State.Canceled]:
                self.file_list_items.remove(file_item)

        paths = list(map(lambda u: u.path(), urls))
        filtered = filter(file_extension_of_path_is_valid, paths)
        grouped = groupby(file_name_without_extension_for_path, filtered)
        file_and_keyfile_paths = map(lambda i: i.file_and_keyfile_paths,
                                     self.file_list_items)
        for key in grouped.keys():
            if len(grouped[key]) != 2:
                continue
            file_path = [path for path in grouped[key]
                         if file_extension_for_path(path) == "mp4"][0]
            keyfile_path = [path for path in grouped[key]
                            if file_extension_for_path(path) == "pgp"][0]
            newItem = FileAndKeyFilePaths(file_path, keyfile_path)
            if newItem not in file_and_keyfile_paths:
                self.file_list_items.append(FileListItem(newItem))
        self.listItemsChanged.emit(self.file_list_items)

    @pyqtSlot(QVariant)
    def onFolderPicked(self, url):
        paths = QDir(url.path()).entryList()
        self.onFilesPicked(
            map(lambda p: QUrl(os.path.join(url.path(), p)), paths))

    @pyqtSlot(QVariant)
    def onOutputDirectoryPicked(self, url):
        self.output_path = url.path()
        self.outputPathChanged.emit(self.output_path)

    @pyqtSlot()
    def onDecryptClicked(self):
        loop = asyncio.get_event_loop()
        for item in self.file_list_items:
            if item.state != FileListItem.State.NotStarted:
                continue

            def pcb(progress, file_item=item):
                self.__on_decrypt_progress(file_item, progress)
            ffmpeg = create_ffmpeg(item.file_and_keyfile_paths, self.output_path,
                                   self.gpg_provider.gpg,
                                   # we have to use parameters with default values here because of
                                   # something called 'late binding' I think. I don't know
                                   # what that means, but without it the callbacks always receive the same arguments
                                   progress_callback=lambda p, item=item:
                                   self.__on_decrypt_progress(item, p),
                                   complete_callback=lambda item=item: self.__on_decrypt_complete(
                                       item),
                                   output_callback=lambda o, item=item: self.__on_ffmpeg_output(item,
                                                                                                o),
                                   gpg_error_callback=lambda e, item=item: self.__on_gpg_error(
                                       item, e),
                                   ffmpeg_error_callback=lambda e, item=item:
                                   self.__on_ffmpeg_error(item, e))
            if ffmpeg is not None:
                self.decrypt_tasks[item] = ffmpeg
                self.ffmpeg_queue.put(ffmpeg)
        self.__launch_ffmpegs_if_available()

    @ pyqtSlot()
    def onGpgSettingsClicked(self):
        pass
        # self.gpgWindow = GpgSettingsWindow(self.gpg_provider)

    __ansi_escape = re.compile(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])')

    def __on_ffmpeg_output(self, file_item, output):
        escaped = self.__ansi_escape.sub('', output)
        self.ffmpeg_outputs[file_item] = self.ffmpeg_outputs.get(
            file_item, '') + '<br>' + escaped
        if file_item == self.selected_item:
            self.statusTextChanged.emit(self.ffmpeg_outputs[file_item])

    def __on_decrypt_progress(self, file_item, progress):
        if file_item.processingState == FileListItem.State.Canceled:
            return
        file_item.update_state(
            FileListItem.State.Processing, progress=f'{progress.frame} frames')
        self.listItemsChanged.emit(self.file_list_items)

    def __on_gpg_error(self, file_item, error):
        print(
            f'error for {file_item.file_and_keyfile_paths.file_path}: {error}')
        file_item.update_state(FileListItem.State.Error,
                               error_message=str(error))
        self.listItemsChanged.emit(self.file_list_items)
        self.ffmpeg_outputs[file_item] = self.ffmpeg_outputs.get(
            file_item, '') + '\n' + f'<font color=#ff0000>{error}</font>'
        if file_item == self.selected_item:
            self.statusTextChanged.emit(self.ffmpeg_outputs[file_item])

    def __on_ffmpeg_error(self, file_item, error):
        print(
            f'error for {file_item.file_and_keyfile_paths.file_path}: {error}')
        file_item.update_state(FileListItem.State.Error,
                               error_message=str(error))
        self.listItemsChanged.emit(self.file_list_items)
        self.ffmpeg_outputs[file_item] = self.ffmpeg_outputs.get(
            file_item, '') + '\n' + f'<font color=#ff0000>{error}</font>'
        if file_item == self.selected_item:
            self.statusTextChanged.emit(self.ffmpeg_outputs[file_item])

        self.running_ffmpegs -= 1

    def __on_decrypt_complete(self, file_item):
        file_item.update_state(FileListItem.State.Done)
        self.listItemsChanged.emit(self.file_list_items)
        del self.decrypt_tasks[file_item]
        self.running_ffmpegs -= 1
        self.__launch_ffmpegs_if_available()

    def event(self, event):
        if (event.type() == QEvent.Close):
            for ffmpeg in self.decrypt_tasks.values():
                print('terminating ffmpeg process')
                # ffmpeg._process.send_signal(signal.SIGKILL)
                ffmpeg.terminate()
        return super().event(event)

    def __launch_ffmpegs_if_available(self):
        print(f'running jobs: {self.running_ffmpegs}')
        while self.running_ffmpegs < MAX_FFMPEG_JOBS and not self.ffmpeg_queue.empty():
            try:
                ffmpeg = self.ffmpeg_queue.get_nowait()
            except QueueEmpty as e:
                continue  # shouldn't really happen
            asyncio.ensure_future(
                ffmpeg.execute(), loop=asyncio.get_event_loop())
            self.running_ffmpegs += 1

    @ pyqtSlot(int)
    def onItemRemoved(self, index):
        item = self.file_list_items[index]
        if item.processingState == FileListItem.State.NotStarted:
            # remove item from list
            del self.file_list_items[index]
            self.listItemsChanged.emit(self.file_list_items)
        elif item.processingState == FileListItem.State.Processing:
            # cancel ffmpeg process
            if item in self.decrypt_tasks:
                self.decrypt_tasks[item].terminate()
                del self.decrypt_tasks[item]
                self.running_ffmpegs -= 1
                self.__launch_ffmpegs_if_available()
            item.update_state(FileListItem.State.Canceled)
            self.listItemsChanged.emit(self.file_list_items)

    @ pyqtSlot(int)
    def onItemSelected(self, index):
        item = self.file_list_items[index]
        self.selected_item = item
        self.statusTextChanged.emit(self.ffmpeg_outputs.get(item, ''))
