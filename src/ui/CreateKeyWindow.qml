import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Window 2.15
import QtQuick.Layouts 1.15
import QtQuick.Dialogs 1.2

ApplicationWindow {
    height: 400
    width: 500
    modality: "ApplicationModal"
    title: "Create key"

    function onCreateKeyClicked(name, email, comment, passphrase, passphraseConfirm) {
        console.log("Base function")
    }

    GridLayout {
        anchors.fill: parent
        anchors.margins: 20
        columns: 2

        Label {
            text: "Name"
            Layout.alignment: Qt.AlignLeft
        }

        TextField {
            id: textFieldName
            Layout.fillWidth: true
        }

        Label {
            text: "Email"
            Layout.alignment: Qt.AlignLeft
        }

        TextField {
            id: textFieldEmail
            Layout.fillWidth: true
            text: "email@email.com"
        }

        Label {
            text: "Comment"
            Layout.alignment: Qt.AlignLeft
        }

        TextField {
            id: textFieldComment
            Layout.fillWidth: true
        }

        Label {
            text: "Passphrase"
        }

        TextField {
            id: textFieldPassphrase
            echoMode: TextInput.Password
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

        Label {
            text: "Key type: ED25519"
        }

        Item {}

        Label {
            id: labelEntropy
            text: "If this takes long, try moving the mouse around to generate entropy."
            visible: false
        }

        Button {
            id: buttonCreateKey
            text: "Create key"
            onClicked: {
                let passphrase = textFieldPassphrase.text
                let passphraseConfirm = textFieldRepeatPassphrase.text
                if (passphrase == "") {
                    labelPassphraseError.visible = true
                    labelPassphraseError.text = "Passphrase can't be emtpy."
                    return
                }

                if (passphrase !== passphraseConfirm) {
                    labelPassphraseError.visible = true
                    labelPassphraseError.text = "Passphrases don't match."
                    return
                }
                let name = textFieldName.text
                let comment = textFieldComment.text
                let email = textFieldEmail.text
                labelEntropy.visible = true
                onCreateKeyClicked(name, email, comment, passphrase, passphraseConfirm)
                createKeyWindow.close()
                textFieldComment.clear()
                textFieldName.clear()
                textFieldPassphrase.clear()
                textFieldRepeatPassphrase.clear()
            }
        }
    }
}
