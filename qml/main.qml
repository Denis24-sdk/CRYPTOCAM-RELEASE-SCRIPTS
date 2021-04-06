import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Window 2.15
import QtQuick.Layouts 1.15
import QtQuick.Dialogs 1.3
import CryptocamCompanion 1.0

ApplicationWindow {
    visible: true
    title: "Cryptocam Companion"
    minimumHeight: 500
    minimumWidth: 800

    CryptocamCompanion {
        id: cryptocam
        Component.onCompleted: {
            cryptocam.init()
        }
    }

    MainWindow {
        id: mainWindow
        cryptocam: cryptocam
        anchors.fill: parent
        buttonOpenKeyWindow.onClicked: {
            keyWindow.visible = true
        }
    }

    KeyWindow {
        id: keyWindow
        visible: false
        cryptocam: cryptocam
        onCreateKeyClicked: {
            createKeyWindow.visible = true
        }
    }

    CreateKeyWindow {
        id: createKeyWindow
        visible: false
        onKeyCreated: {
            cryptocam.createKey(name, passphrase, passphraseConfirm)
        }
    }

    MessageDialog {
        title: "Error"
        id: errorDialog
        visible: false
        modality: Qt.ApplicationModal
    }
    Component {
        id: askPassphraseDialogComponent

        ApplicationWindow {
            id: askPassphraseDialog
            visible: false
            modality: Qt.ApplicationModal
            title: "Enter passphrase"
            width: 300
            height: 150
            property string keyName: ""
            property string error: ""

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 20
                Text {
                    Layout.fillWidth: true
                    wrapMode: Text.WrapAtWordBoundaryOrAnywhere
                    text: "Enter passphrase for " + askPassphraseDialog.keyName
                }

                TextField {
                    id: askPassphraseInput
                    Layout.fillWidth: true
                    echoMode: TextField.Password
                }

                Text {
                    text: askPassphraseDialog.error
                    Layout.fillWidth: true
                    visible: askPassphraseDialog.error
                    color: "red"
                }
                Button {
                    id: askPassphraseOkButton
                    text: "Ok"
                    onClicked: {
                        let passphrase = askPassphraseInput.text
                        askPassphraseDialog.close()
                        cryptocam.passphraseAsked(passphrase)
                    }
                }
            }
        }
    }


    Connections {
        target: cryptocam
        function onAskPassphrase(keyName, error) {
            console.log("askPassphrase " + keyName)
            let dialog = askPassphraseDialogComponent.createObject(null)
            dialog.keyName = keyName
            dialog.error = error
            dialog.visible = true
        }

        function onErrorChanged() {
            let error = cryptocam.error
            if (error === null) {
                errorDialog.visible = false
            } else {
                errorDialog.text = error
                errorDialog.visible = true
            }
        }
    }

}
