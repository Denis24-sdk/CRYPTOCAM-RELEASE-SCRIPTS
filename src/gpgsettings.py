import os

import gnupg
from PyQt5.QtCore import (QObject, Qt, QUrl, QVariant, pyqtProperty,
                          pyqtSignal, pyqtSlot)
from PyQt5.QtQuick import QQuickView
from qasync import QtCore

from create_key import CreateKeyWindow


class GpgProvider(QObject):
    def __init__(self, config):
        super().__init__()
        self.gpg = gnupg.GPG(gnupghome=config.gpg_home)
        self.gpg.verbose = True

    def recreate_with_gpg_home(self, gpg_home):
        self.gpg = gnupg.GPG(gnupghome=gpg_home)


class Key(QObject):
    def __init__(self, fingerprint, uids, has_private):
        super().__init__(None)
        self.__fingerprint = fingerprint
        self.uids = uids
        self.has_private = has_private

    @pyqtProperty("QString")
    def exportName(self):
        return self.uids[0].replace(' ', '_')

    @pyqtProperty("QString")
    def fingerprint(self):
        return self.__fingerprint

    @pyqtProperty("QString")
    def uidsStr(self):
        return ", ".join([str(uid) for uid in self.uids])

    @pyqtProperty("QString")
    def hasPrivate(self):
        if self.has_private:
            return "Public, Private"
        else:
            return "Public"


def compare_keys(k1, k2):
    if k1.has_private and not k2_has_private:
        return -1
    elif k2.has_private and not k1.has_private:
        return 1
    return cmp(k1.uids, k2.uids)


class GpgSettingsController(QObject):
    keysChanged = pyqtSignal("QVariant")
    gpgHomePathChanged = pyqtSignal("QString")
    exportPrivateButtonEnabled = pyqtSignal(bool)
    exportPublicButtonEnabled = pyqtSignal(bool)
    error = pyqtSignal("QString")
    __keys = {}

    gpg_provider = None

    def __init__(self, parent=None):
        super().__init__()
        # self.rootContext().setContextProperty("gpgSettingsWindow", self)
        # self.setTitle('GPG Settings')
        # path = os.path.dirname(__file__)
        # self.setSource(QUrl(os.path.join(path, 'ui/GpgSettingsWindow.qml')))
        # self.setModality(Qt.ApplicationModal)
        # self.show()

    @pyqtSlot("QVariant")
    def init(self, gpg_provider):
        self.gpg_provider = gpg_provider
        self.gpgHomePathChanged.emit(self.gpg_provider.gpg.gnupghome)
        self.__keys.clear()
        self.__get_keys()

    def __get_keys(self):
        gpg = self.gpg_provider.gpg
        kpubs = gpg.list_keys()
        kprivs = gpg.list_keys(True)
        for kpub in kpubs:
            key_item = Key(kpub['fingerprint'], kpub['uids'], False)
            self.__keys[key_item.fingerprint] = key_item
        for kpriv in kprivs:
            fp = kpriv['fingerprint']
            if fp in self.__keys:
                self.__keys[fp].has_private = True
            else:
                print(f'Key {fp} has private key but no public key!')
        all_keys = list(self.__keys.values())
        with_priv = list(filter(lambda k: k.has_private, all_keys))
        without_priv = list(filter(lambda k: not k.has_private, all_keys))
        all_sorted = sorted(with_priv, key=lambda k: k.uids) + \
            sorted(without_priv, key=lambda k: k.uids)
        self.keysChanged.emit(all_sorted)

    @pyqtSlot(QUrl)
    def onGpgHomeChanged(self, url):
        self.gpg_provider.recreate_with_gpg_home(url.path())
        self.__keys.clear()
        self.__get_keys()
        self.gpgHomePathChanged.emit(url.path())

    @pyqtSlot("QString", "QUrl", "QString")
    def onPrivateKeyExport(self, fingerprint, file, passphrase):
        # TODO check that this succeeds, don't create file if export fails and show status
        # message
        key = self.gpg_provider.gpg.export_keys(
            fingerprint, True, passphrase=passphrase)
        if not key:
            self.error.emit("Error exporting key.")
        else:
            path = file.path()
            try:
                with open(path, 'w') as f:
                    f.write(key)
            except Exception as e:
                self.error.emit(str(e))

    @pyqtSlot("QString", "QUrl")
    def onPublicKeyExport(self, fingerprint, file):
        key = self.gpg_provider.gpg.export_keys(fingerprint)
        print(key)
        path = file.path()
        if not key:
            self.error.emit("Error exporting key.")
        else:
            try:
                with open(path, 'w') as f:
                    f.write(key)
            except Exception as e:
                self.error.emit(str(e))

    @pyqtSlot("QUrl")
    def onImportKeys(self, url):
        gpg = self.gpg_provider.gpg
        try:
            with open(url.path(), 'rb') as f:
                import_result = gpg.import_keys(
                    f.read())
                # print(f'\n\nRESULT: {import_result.results}')
        except Exception as e:
            self.error.emit(str(e))
        self.__get_keys()

    @pyqtSlot("QString")
    def onSelectedKeyChanged(self, fingerprint):
        if fingerprint not in self.__keys:
            print('Selected key not found!')
            return
        key = self.__keys[fingerprint]
        self.exportPrivateButtonEnabled.emit(key.has_private)
        self.exportPublicButtonEnabled.emit(True)

    @pyqtSlot("QString", "QString", "QString", "QString", "QString")
    def onCreateKey(self, name, email, comment, passphrase, passphrase_confirm):
        assert passphrase == passphrase_confirm
        gpg: gnupg.GPG = self.gpg_provider.gpg
        input_data = gpg.gen_key_input(key_type="eddsa",
                                       key_curve="Ed25519",
                                       subkey_curve='Curve25519',
                                       subkey_usage='encrypt',
                                       subkey_type='ecdh',
                                       name_real=name,
                                       name_email=email, name_comment=comment,
                                       passphrase=passphrase)
        print(input_data)
        gen_key = gpg.gen_key(input_data)
        if not gen_key:
            self.error.emit(gen_key.stderr)
        self.__get_keys()
