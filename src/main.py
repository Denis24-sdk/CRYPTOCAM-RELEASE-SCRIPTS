import argparse
import asyncio
import json
import logging
import os
import subprocess
import sys

import ffmpeg
from PyQt5.QtCore import QObject, QThreadPool, QUrl, QCoreApplication
from PyQt5.QtGui import QGuiApplication, QIcon
from PyQt5.QtWidgets import QApplication
from PyQt5.QtQml import QQmlApplicationEngine, qmlRegisterType, QQmlEngine, QQmlComponent
from qasync import QEventLoop

import file_list
import gpgsettings
from config import Config
from mainwindow import MainWindowController

if __name__ == '__main__':
    logging.getLogger('gnupg').setLevel(logging.ERROR)
    parser = argparse.ArgumentParser()
    parser.add_argument('--gpg-home', help='GnuPG gome location', default=None)
    parser.add_argument(
        '--test', help='For testing only. Exits immediately', action='store_true')

    # appimage-builder adds an empty argument if no arguments are provided, so remove it
    argv = [s for s in sys.argv[1:] if len(s) > 0]
    args = parser.parse_args(argv)

    print(os.environ['HOME'])

    out = subprocess.run(["gpg", "--version"], capture_output=True)
    print(out.stdout.decode('unicode-escape'))
    out = subprocess.run(["ffmpeg", "-version"], capture_output=True)
    print(out.stdout.decode('unicode-escape'))

    if args.test:
        sys.exit(0)
    if not "QT_QUICK_CONTROLS_STYLE" in os.environ:
        os.environ["QT_QUICK_CONTROLS_STYLE"] = "Fusion"

    config = Config(args.gpg_home)

    app = QApplication(argv)
    QIcon.setThemeName('default')

    qmlRegisterType(file_list.FileListItem.State, "State", 1, 0, "State")
    qmlRegisterType(gpgsettings.Key, "Key", 1, 0, "Key")
    qmlRegisterType(MainWindowController, "MainWindowController",
                    1, 0, "MainWindowController")
    qmlRegisterType(gpgsettings.GpgSettingsController,
                    "GpgSettingsController", 1, 0, "GpgSettingsController")
    qmlRegisterType(gpgsettings.GpgProvider,
                    "GpgProvider", 1, 0, 'GpgProvider')

    loop = QEventLoop(app)
    asyncio.set_event_loop(loop)

    engine = QQmlApplicationEngine(parent=app)
    gpg_provider = gpgsettings.GpgProvider(config)
    mainWindowController = MainWindowController(gpg_provider)
    context = engine.rootContext()
    context.setContextProperty("controller", mainWindowController)
    context.setContextProperty("gpg_provider", gpg_provider)
    path = os.path.dirname(__file__)
    engine.load(QUrl(os.path.join(path, 'ui/MainWindow.qml')))
    ret = loop.run_forever()
    del mainWindowController
    sys.exit(ret)
