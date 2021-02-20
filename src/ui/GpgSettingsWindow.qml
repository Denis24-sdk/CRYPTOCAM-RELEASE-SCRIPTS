import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Window 2.15
import QtQuick.Layouts 1.15
import QtQuick.Dialogs 1.2
import Qt.labs.platform 1.1 as PlatformDialog

import Key 1.0
import GpgSettingsController 1.0

ApplicationWindow {
    width: 800
    height: 600
    modality: "WindowModal"
    visible: true
    title: "Gpg Settings"


    ListModel {
        id: gpgKeyListModel
    }

    property GpgSettingsController gpgSettingsWindow: GpgSettingsController {
    }

    property CreateKeyWindow createKeyWindow: CreateKeyWindow {
        function onCreateKeyClicked(name, email, comment, passphrase, passphraseConfirm) {
            gpgSettingsWindow.onCreateKey(name, email, comment, passphrase, passphraseConfirm)
        }
    }

    Component.onCompleted: {
        // For some reason the default filename (FileDialog.currentFile) is not set and displayed
        // properly before the dialog is shown the first time, so we work around that here
        exportPublicDialog.visible = true
        exportPublicDialog.visible = false
        exportPrivateDialog.visible = true
        exportPrivateDialog.visible = false
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 20

        RowLayout {
            Layout.fillWidth: true
            Label {
                Layout.alignment: Qt.AlignLeft
                text: "GPG Home:"
            }
            TextField {
                id: textFieldGpgHome
                Layout.alignment: Qt.AlignLeft
                Layout.fillWidth: true
                readOnly: true
            }

            Button {
                text: "Change"
                Layout.alignment: Qt.AlignRight
                onClicked: {
                    gpgHomeDialog.visible = true
                }
            }
        }

        Label {
            Layout.alignment: Qt.AlignLeft
            text: "Your keys:"
            font.pointSize: 16
            Layout.topMargin: 30
        }

        ListView {
            id: listKeyView
            Layout.fillWidth: true
            Layout.preferredHeight: 300
            Layout.fillHeight: true
            model: gpgKeyListModel
            clip: true
            delegate: MouseArea {
                id: keyListItem
                width: ListView.view.width
                height: 60
                clip: true
                onClicked: {
                    keyListItem.ListView.view.currentIndex = index
                }
                RowLayout {
                    anchors.fill: parent
                    ColumnLayout {
                        Layout.alignment: Qt.AlignLeft
                        Text {
                            text: fingerprint
                        }
                        Text {
                            text: uidsStr
                            Layout.fillWidth: true
                            elide: Text.ElideRight
                        }
                    }

                    Text {
                        text: hasPrivate
                        Layout.alignment: Qt.AlignRight
                    }
                }
                Rectangle {
                    width: parent.width
                    anchors.bottom: parent.bottom
                    height: 1
                    color: 'grey'
                }
            }
            highlight: Rectangle { color: "lightsteelblue"; }
        }
        RowLayout {
            Layout.fillWidth: true
            Button {
                id: buttonCreateKey
                text: "Create key"
                Layout.alignment: Qt.AlignLeft
                onClicked: {
                    createKeyWindow.visible = true
                }
            }
            Button {
                id: buttonImportKey
                text: "Import key"
                Layout.alignment: Qt.AlignLeft
                onClicked: {
                    importKeyDialog.visible = true
                }
            }
            Button {
                id: buttonExportPrivate
                text: "Export private key"
                Layout.alignment: Qt.AlignRight
                enabled: false
                onClicked: {
                    let index = listKeyView.currentIndex
                    let currentKeyItem = gpgKeyListModel.get(index)
                    let filename = currentKeyItem.exportName + ".priv"
                    passphraseExportDialog.fingerprint = currentKeyItem.fingerprint
                    exportPrivateDialog.currentFile = filename
                    exportPrivateDialog.visible = true
                }
            }
            Button {
                id: buttonExportPublic
                text: "Export public key"
                Layout.alignment: Qt.AlignRight
                enabled: false
                onClicked: {
                    let index = listKeyView.currentIndex
                    let currentKeyItem = gpgKeyListModel.get(index)
                    let filename = currentKeyItem.exportName + ".pub"
                    exportPublicDialog.currentFile = filename
                    exportPublicDialog.visible = true
                }
            }
        }
    }

    PlatformDialog.FolderDialog {
        id: gpgHomeDialog
        title: "Please choose a folder"
        onAccepted: {
            gpgSettingsWindow.onGpgHomeChanged(gpgHomeDialog.folder)
        }
        modality: "ApplicationModal"
    }

    PlatformDialog.FileDialog {
        id: exportPublicDialog
        title: "Export public key"
        modality: "ApplicationModal"
        fileMode: PlatformDialog.FileDialog.SaveFile
        defaultSuffix: ".pub"
        onAccepted: {
            let fingerprint = gpgKeyListModel.get(listKeyView.currentIndex).fingerprint
            gpgSettingsWindow.onPublicKeyExport(fingerprint, file)
        }
    }

    PlatformDialog.FileDialog {
        id: exportPrivateDialog
        title: "Export private key"
        modality: "ApplicationModal"
        fileMode: PlatformDialog.FileDialog.SaveFile
        defaultSuffix: ".priv"
        onAccepted: {
            passphraseExportDialog.visible = true
        }
    }

    PlatformDialog.FileDialog {
        id: importKeyDialog
        title: "Import key"
        modality: "ApplicationModal"
        fileMode: PlatformDialog.FileDialog.OpenFile
        onAccepted: {
                    let file = importKeyDialog.file
                    gpgSettingsWindow.onImportKeys(file)
        }
    }

    Window {
        id: passphraseExportDialog
        modality: "ApplicationModal"
        property string fingerprint: ""
        title: "Enter passphrase to export"
        width: 300
        height: 150
        ColumnLayout {
            width: 300
            Label {
                text: "Enter passphrase"
            }
            TextField {
                Layout.fillWidth: true
                id: passphraseExportDialogTextField
                echoMode: TextInput.Password
            }
            Button {
                text: "Export"
                onClicked: {
                    let file = exportPrivateDialog.file
                    gpgSettingsWindow.onPrivateKeyExport(passphraseExportDialog.fingerprint, file, passphraseExportDialogTextField.text)
                    passphraseExportDialog.visible = false
                    passphraseExportDialogTextField.clear()
                }
            }
        }
    }

    MessageDialog {
        id: errorDialog
        title: "Error"
    }

    Connections {
        target: listKeyView

        function onCurrentIndexChanged() {
            let index = listKeyView.currentIndex
            gpgSettingsWindow.onSelectedKeyChanged(gpgKeyListModel.get(index).fingerprint)
        }
    }

    Connections {
        target: gpgSettingsWindow

        function onGpgHomePathChanged(path) {
            textFieldGpgHome.text = path
        }

        function onKeysChanged(keys) {
            let selectedIndex = listKeyView.currentIndex
            gpgKeyListModel.clear()
            gpgKeyListModel.append(keys)
            listKeyView.currentIndex = selectedIndex
        }

        function onExportPrivateButtonEnabled(enabled) {
            buttonExportPrivate.enabled = enabled
        }

        function onExportPublicButtonEnabled(enabled) {
            buttonExportPublic.enabled = enabled
        }

        function onError(message) {
            errorDialog.text = message
            errorDialog.visible = true
        }
    }
}
