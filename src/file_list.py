import enum
import os

from PyQt5.QtCore import (Q_ENUM, QObject, Qt, QVariant, pyqtProperty,
                          pyqtSignal, pyqtSlot)


def file_extension_for_path(path):
    path = os.path.split(path)
    filename = path[-1]
    split = filename.split(".")
    return split[-1]


def file_extension_of_path_is_valid(path):
    return file_extension_for_path(path) in ["mp4", "pgp"]


def file_name_without_extension_for_path(path):
    path = os.path.split(path)
    filename = path[-1]
    split = filename.split(".")
    return split[0]


class FileAndKeyFilePaths:
    def __init__(self, file_path, keyfile_path):
        self.file_path = file_path
        self.keyfile_path = keyfile_path

    def __eq__(self, other):
        if isinstance(other, FileAndKeyFilePaths):
            return str(self.file_path) == str(other.file_path) and str(self.keyfile_path) == str(other.keyfile_path)
        str(other.keyfile_path)

    def __hash__(self):
        return hash((str(self.file_path), str(self.keyfile_path)))


class FileListItem(QObject):
    class State(QObject):
        NotStarted = "NotStarted"
        Processing = "Processing"
        Error = "Error"
        Done = "Done"
        Canceled = "Canceled"

    stateChanged = pyqtSignal("QString", "QString")

    def __init__(self, file_and_keyfile_paths, parent=None):
        super().__init__(parent)
        self.file_and_keyfile_paths = file_and_keyfile_paths
        self.state = FileListItem.State.NotStarted
        self.__error_message = None
        self.__progress = None

    def update_state(self, state, progress=None, error_message=None):
        self.state = state
        self.__error_message = error_message
        self.__progress = progress

    @pyqtProperty("QString", constant=True)
    def name(self):
        return file_name_without_extension_for_path(self.file_and_keyfile_paths.file_path)

    @pyqtProperty("QString")
    def processingState(self):
        return self.state

    @pyqtProperty("QString")
    def errorMessage(self):
        return self.__error_message

    @pyqtProperty("QString")
    def progress(self):
        return self.__progress
