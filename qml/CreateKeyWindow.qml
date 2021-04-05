import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Window 2.15
import QtQuick.Layouts 1.15
import QtQuick.Dialogs 1.3

ApplicationWindow {
    id: createKeyWindow
    height: 400
    width: 500
    modality: "ApplicationModal"
    title: "Create key"

    signal keyCreated(string name, string passphrase, string passphraseConfirm)

    GridLayout {
        anchors.fill: parent
        anchors.margins: 20
        columns: 2

        Label {
            text: "Key name"
            Layout.alignment: Qt.AlignLeft
        }

        TextField {
            id: textFieldName
            Layout.fillWidth: true
            selectByMouse: true
        }

        Label {
            text: "Passphrase"
        }

        TextField {
            id: textFieldPassphrase
            echoMode: TextInput.Password
            selectByMouse: true
            Layout.fillWidth: true
            onTextChanged: {
                labelPassphraseError.visible = false
            }
        }

        Label {
            text: "Passphrase"
        }

        TextField {
            id: textFieldRepeatPassphrase
            echoMode: TextInput.Password
            selectByMouse: true
            Layout.fillWidth: true
            onTextChanged: {
                labelPassphraseError.visible = false
            }
        }

        Label {
            id: labelPassphraseError
            Layout.columnSpan: 2
            visible: false
        }

        Button {
            id: buttonCreateKey
            text: "Create key"
            onClicked: {

                let passphrase = textFieldPassphrase.text
                let passphraseConfirm = textFieldRepeatPassphrase.text
                let name = textFieldName.text
                if (!name) {
                    labelPassphraseError.visible = true
                    labelPassphraseError.text = "Name can't be emtpy."
                    return
                }
                if (passphrase === "") {
                    labelPassphraseError.visible = true
                    labelPassphraseError.text = "Passphrase can't be emtpy."
                    return
                }

                if (passphrase !== passphraseConfirm) {
                    labelPassphraseError.visible = true
                    labelPassphraseError.text = "Passphrases don't match."
                    return
                }
                createKeyWindow.close()
                createKeyWindow.keyCreated(name, passphrase, passphraseConfirm)
                textFieldName.clear()
                textFieldPassphrase.clear()
                textFieldRepeatPassphrase.clear()
            }
        }
    }
}
