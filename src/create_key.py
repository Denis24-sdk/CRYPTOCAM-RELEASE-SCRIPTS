import os
import gnupg
from PyQt5.QtCore import Qt, QUrl, pyqtSlot
from PyQt5.QtQuick import QQuickView
from qasync import QtCore


class CreateKeyWindow(QQuickView):
    def __init__(self, gpg_provider, callback):
        super().__init__()
        self.gpg_provider = gpg_provider
        self.callback = callback
        self.setTitle('Create GPG key')
        self.rootContext().setContextProperty("createKeyWindow", self)
        path = os.path.dirname(__file__)
        self.setSource(QUrl(os.path.join(path, 'ui/CreateKeyWindow.qml')))
        self.setModality(Qt.ApplicationModal)
        self.show()
