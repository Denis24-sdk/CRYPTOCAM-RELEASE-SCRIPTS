import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Window 2.15
import QtQuick.Layouts 1.15
import QtQuick.Dialogs 1.3

import State 1.0
import MainWindowController 1.0
import GpgProvider 1.0

ApplicationWindow {
    width: 800
    height: 800
    visible: true
    title: "Cryptocam Companion"
    DropArea {
        id: dropArea
        onEntered: {
            if (drag.hasUrls) {
                drag.accept()
            }
        }

        onDropped: {
            controller.onFilesPicked(drop.urls)
        }
    }

    ListModel {
        id: listFileModel
    }

    property GpgSettingsWindow gpgSettingsWindow: GpgSettingsWindow {
        visible: false
        Component.onCompleted: {
            gpgSettingsWindow.init(gpg_provider)
        }
    }
    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 20

        StackLayout {
            id: stackLayout
            currentIndex: 0

            Layout.fillWidth: true
            Layout.preferredHeight: 300

            Label {
                Layout.fillWidth: true
                Layout.fillHeight: true
                text: "Drag and drop files here, or click 'Open files'"
                font.pointSize: 24
                wrapMode: "WordWrap"
                verticalAlignment: "AlignVCenter"
                horizontalAlignment: "AlignHCenter"
            }

            ListView {
                id: listViewFiles
                model: listFileModel
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                delegate: MouseArea {
                    id: fileListItem
                    width: ListView.view.width
                    height: 40
                    onClicked: {
                        fileListItem.ListView.view.currentIndex = index
                        controller.onItemSelected(index)
                    }

                    RowLayout {
                        anchors.fill: parent
                        Text {
                            Layout.alignment: Qt.AlignLeft | Qt.AlignVCenter
                            Layout.leftMargin: 8
                            text: name + state
                        }
                        Label {
                            id: labelFileStatus
                            Layout.alignment: Qt.AlignRight | Qt.AlignVCenter
                            text: getText()
                            visible: [ "Canceled", "Processing", "Done", "Error" ].includes(processingState)
                            function getText() {
                                if (processingState === "Error")
                                    return errorMessage
                                else if (processingState === "Processing")
                                    return progress
                                return processingState
                            }
                        }

                        Button {
                            id: buttonRemoveFile
                            Layout.alignment: Qt.AlignRight | Qt.AlignVCenter
                            Layout.leftMargin: 8
                            text: getText()
                            function getText() {
                                if (processingState === "NotStarted")
                                    return "Remove"
                                else if (processingState === "Processing")
                                    return "Cancel"
                                return null
                            }
                            visible: [ "NotStarted", "Processing" ].includes(processingState)
                            onClicked: {
                                controller.onItemRemoved(index)
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
                highlight: Rectangle { color: "lightsteelblue"; }
            }
        }
        RowLayout {
            spacing: 16
            Layout.fillWidth: true
            Button {
                id: buttonOpenFiles
                text: "Open files"
                onClicked: {
                    openFileDialog.visible = true
                }
            }
            Button {
                id: buttonOpenFolder
                text: "Open folder"
                onClicked: {
                    openFolderDialog.visible = true
                }
            }
            Item { Layout.fillWidth: true }
            Button {
                id: buttonGpgSettings
                text: "GPG Settings"
                onClicked: {
//                    controller.onGpgSettingsClicked()
                    gpgSettingsWindow.visible = true
                }
            }
            Button {
                id: buttonDecrypt
                text: "Decrypt"
                onClicked: {
                    controller.onDecryptClicked()
                }
            }
        }

        ScrollView {
            id: scrollViewStatus
            Layout.preferredHeight: 300
            Layout.fillWidth: true

            ScrollBar.horizontal.policy: ScrollBar.AsNeeded
            ScrollBar.vertical.policy: ScrollBar.AsNeeded

            TextArea {
                id: textAreaStatus
                Layout.fillWidth: true
                selectByMouse: true
                readOnly: true
                textFormat: "RichText"
            }
        }


        RowLayout {
            Layout.fillWidth: true
            Label {
                text: "Output location:"
                Layout.alignment: Qt.AlignLeft
            }
            TextField {
                id: textFieldOutPath
                Layout.fillWidth: true
                text: ""
                wrapMode: TextEdit.NoWrap
                Layout.minimumWidth: 400
                readOnly: true
            }
            Button {
                id: buttonChooseOutPath
                text: "Choose location"
                Layout.alignment: Qt.AlignRight
                onClicked: {
                    outputDirectoryDialog.visible = true
                }
            }
        }

    }
    Connections {
        target: listFileModel

        function onRowsInserted(parent, first, last) {
            updateStackLayout()
        }

        function onRowsRemoved(parent, first, last) {
            updateStackLayout()
        }

        function updateStackLayout() {
            let listVisible = listFileModel.rowCount() > 0
            if (listVisible) {
                stackLayout.currentIndex = 1
            }
            else {
                stackLayout.currentIndex = 0
            }
        }
    }

    Connections {
        target: controller

        function onListItemsChanged(fileListItems) {
            let index = listViewFiles.currentIndex
            listFileModel.clear()
            listFileModel.append(fileListItems)
            listViewFiles.currentIndex = index
        }

        function onStatusTextChanged(text) {
            textAreaStatus.text = text
            if (textAreaStatus.contentHeight > scrollViewStatus.height) {
                scrollViewStatus.ScrollBar.vertical.position = 1.0 - (scrollViewStatus.height / textAreaStatus.contentHeight)
            }
        }

        function onOutputPathChanged(path) {
            textFieldOutPath.text = path
        }
    }

    FileDialog {
        id: openFileDialog
        title: "Open files"
        folder: shortcuts.home
        selectExisting: true
        selectMultiple: true
        nameFilters: [ "Cryptocam output files (*.mp4 *.pgp)" ]
        onAccepted: {
            controller.onFilesPicked(openFileDialog.fileUrls)
        }
        modality: "ApplicationModal"
    }

    FileDialog {
        id: openFolderDialog
        title: "Open folder"
        folder: shortcuts.home
        selectExisting: true
        selectFolder: true
        onAccepted: {
            controller.onFolderPicked(openFolderDialog.fileUrl)
        }
        modality: "ApplicationModal"
    }

    FileDialog {
        id: outputDirectoryDialog
        title: "Choose output directory"
        folder: shortcuts.home
        selectFolder: true
        onAccepted: {
            controller.onOutputDirectoryPicked(outputDirectoryDialog.fileUrl)
        }
        modality: "ApplicationModal"
    }

}
