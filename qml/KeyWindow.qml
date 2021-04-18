import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Window 2.15
import QtQuick.Layouts 1.15
import QtQuick.Dialogs 1.3
import QtQml.Models 2.1
import CryptocamCompanion 1.0

ApplicationWindow {
        minimumWidth: 850
        minimumHeight: 700
        modality: Qt.ApplicationModal
        visible: true
        title: "Cryptocam keys"
        id: keyWindow
        property CryptocamCompanion cryptocam: null
        signal createKeyClicked()

        GridLayout {
            anchors.fill: parent
            anchors.margins: 20
            columns: 2

            RowLayout {
                Layout.fillWidth: true
                Layout.columnSpan: 2
                Label {
                    Layout.alignment: Qt.AlignLeft
                    text: "Keyring location:"
                }
                TextField {
                    id: keyRingPathView
                    Layout.alignment: Qt.AlignLeft
                    Layout.fillWidth: true
                    readOnly: true
                    text: cryptocam.keyringPath
                }

                Button {
                    text: "Change"
                    Layout.alignment: Qt.AlignRight
                    onClicked: {
                        keyringPathDialog.visible = true
                    }
                }
            }
            Label {
                Layout.columnSpan: 2
                Layout.fillWidth: true
                text: "Keyring does not exists. It will be created."
                visible: !cryptocam.keyringPathExists
            }

            Label {
                Layout.alignment: Qt.AlignLeft
                Layout.columnSpan: 2
                text: "Your keys:"
                font.pointSize: 16
                Layout.topMargin: 30
            }

            ListView {
                id: listKeyView
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredHeight: 300
                Layout.preferredWidth: 500
                model: DelegateModel {
                    id: keyDelegateModel
                    model: cryptocam.keyListModel
                    onCountChanged: {
                        if (count > 0) {
                            listKeyView.currentIndex = 0
                            refreshQrCode()
                        }
                    }
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
                                    text: name
                                }
                                Text {
                                    text: publicKey
                                    Layout.fillWidth: true
                                    elide: Text.ElideRight
                                }
                            }
                        }
                        Rectangle {
                            width: parent.width
                            anchors.bottom: parent.bottom
                            height: 1
                            color: 'grey'
                        }
                    }
                }
                clip: true
                onCurrentIndexChanged: {
                    refreshQrCode()
                }
                highlightMoveDuration: 1
                highlightResizeDuration: 1
                highlight: Rectangle { color: "lightsteelblue"; }
            }

            ColumnLayout {
                Layout.preferredWidth: 400
                visible: keyDelegateModel.count > 0
                Image {
                    id: keyQrCodeView
                    Layout.fillWidth: true
                    fillMode: Image.PreserveAspectFit
                }

                Button {
                    text: "Delete"
                    Layout.alignment: Qt.AlignRight
                    onClicked: {
                        cryptocam.deleteKey(listKeyView.currentIndex)
                    }
                }
            }


            RowLayout {
                Layout.fillWidth: true
                Layout.columnSpan: 2
                Button {
                    id: buttonCreateKey
                    text: "Create key"
                    Layout.alignment: Qt.AlignLeft
                    onClicked: {
                        keyWindow.createKeyClicked()
                    }
                }
            }
        }

        Dialog {
            id: deleteKeyDialog
            visible: false
            modality: Qt.ApplicationModal
            standardButtons: Dialog.Ok | Dialog.Cancel
            title: "Delete key"
            property int keyIndexToDelete: -1
            onAccepted: {
                deleteKeyDialog.visible = false
                cryptocam.reallyDeleteKey(listKeyView.currentIndex)
            }
            onRejected: {
                deleteKeyDialog.visible = false
            }

            Text {
                id: deleteKeyMessage
            }
        }

        FileDialog {
            id: keyringPathDialog
            title: "Please choose a keyring folder"
            selectFolder: true
            onAccepted: {
                cryptocam.keyringPath = keyringPathDialog.fileUrl
            }
            modality: "ApplicationModal"
        }

        function refreshQrCode() {
            let currentIndex = listKeyView.currentIndex
            if (currentIndex >= 0) {
                let img = keyDelegateModel.items.get(currentIndex).model.qrCode
                keyQrCodeView.source = "data:image/svg," + img
            }
        }

        Connections {
            target: cryptocam

            function onKeyringPathChanged() {
                refreshQrCode()
            }

            function onDeleteKeyConfirm(keyName) {
                deleteKeyMessage.text = "Delete key '" + keyName + "'?"
                deleteKeyDialog.visible = true
            }
        }

        Connections {
            target: cryptocam.keyListModel

            function onDataChanged(_, _, _) {
                console.log("ondatachanged")
                refreshQrCode()
            }
        }
}
