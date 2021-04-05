import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Window 2.15
import QtQuick.Layouts 1.15
import QtQuick.Dialogs 1.3
import CryptocamCompanion 1.0

Item {
    property CryptocamCompanion cryptocam: null
    property alias buttonOpenKeyWindow: buttonOpenKeyWindow
    property alias dropArea: dropArea
    property bool hasFiles: false

    DropArea {
        anchors.fill: parent
        id: dropArea
        onEntered: {
            if (drag.hasUrls) {
                drag.accept()
            }
        }
        onDropped: {
            let urls = []
            for (let i in drop.urls) {
                urls += drop.urls[i]
                if (i < drop.urls.length - 1) {
                    urls += ' '
                }
            }
            cryptocam.addFiles(urls)
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 20

        StackLayout {
            id: stackLayout
            currentIndex: hasFiles ? 1 : 0
            Layout.fillWidth: true
            Layout.fillHeight: true
            Layout.preferredHeight: 400

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
                id: fileListView
                model: cryptocam.fileListModel
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                delegate: MouseArea {
                    id: fileListItem
                    width: ListView.view.width
                    height: 40
                    onClicked: {
                        fileListView.currentIndex = index
                    }
                    RowLayout {
                        anchors.fill: parent
                        Text {
                            Layout.alignment: Qt.AlignLeft | Qt.AlignVCenter
                            Layout.leftMargin: 8
                            Layout.maximumWidth: 250
                            text: name
                            elide: Text.ElideRight
                        }
                        Label {
                            id: labelFileStatus
                            Layout.alignment: Qt.AlignRight | Qt.AlignVCenter
                            visible: [ "Canceled", "Processing", "Done", "Error" ].some(s => s == status)
                            text: getText()
                            function getText() {
                                if (status == "Error") {
                                    return error
                                }
                                else if (status == "Processing") {
                                    if (progressPercent < 10) { //hack to keep the progressbar from jumping around
                                        return " " + progressPercent + "%"
                                    }
                                    return progressPercent + "%"
                                }

                                return status
                            }
                        }

                        ProgressBar {
                            visible: status == "Processing"
                            Layout.preferredWidth: 150
                            value: progressPercent
                            from: 0
                            to: 100
                        }

                        Button {
                            id: buttonRemoveFile
                            Layout.alignment: Qt.AlignRight | Qt.AlignVCenter
                            Layout.leftMargin: 8
                            visible: [ "Added", "Processing" ].some(s => s == status)
                            text: getText()
                            function getText() {
                                if (status == "Added")
                                    return "Remove"
                                else if (status == "Processing")
                                    return "Cancel"
                                return null
                            }
                            onClicked: {
                                cryptocam.removeFile(index)
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
                highlightMoveDuration: 1 // this is an obnoxious animation
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
                id: buttonOpenKeyWindow
                text: "Manage keys"
            }
            Button {
                id: buttonDecrypt
                text: "Decrypt"
                onClicked: {
                    cryptocam.decryptClicked()
                }
            }
        }

//        ScrollView {
//            id: scrollViewStatus
//            Layout.preferredHeight: 300
//            Layout.fillWidth: true

//            ScrollBar.horizontal.policy: ScrollBar.AsNeeded
//            ScrollBar.vertical.policy: ScrollBar.AsNeeded

//            TextArea {
//                id: textAreaStatus
//                Layout.fillWidth: true
//                selectByMouse: true
//                readOnly: true
//                textFormat: "RichText"
//            }
//        }

        RowLayout {
            Layout.fillWidth: true
            Label {
                text: "Output location:"
                Layout.alignment: Qt.AlignLeft
            }
            TextField {
                id: textFieldOutPath
                Layout.fillWidth: true
                text: cryptocam.outputPath
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
    FileDialog {
        id: openFileDialog
        title: "Open files"
        folder: shortcuts.home
        selectExisting: true
        selectMultiple: true
        nameFilters: [ "Cryptocam output files (*)" ]
        onAccepted: {
            let urls = []
            for (let i in fileUrls) {
                urls += fileUrls[i].toString()
                if (i < fileUrls.length - 1) {
                    urls += ' '
                }
            }
            cryptocam.addFiles(urls)
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
            cryptocam.addFiles(openFolderDialog.fileUrl)
        }
        modality: "ApplicationModal"
    }
    FileDialog {
        id: outputDirectoryDialog
        title: "Choose output directory"
        folder: shortcuts.home
        selectFolder: true
        onAccepted: {
            cryptocam.outputPath = outputDirectoryDialog.fileUrl
        }
        modality: "ApplicationModal"
    }
    Connections {
        target: cryptocam.fileListModel
        function onRowsInserted(parent, first, last) {
            mainWindow.hasFiles = cryptocam.fileListModel.rowCount() > 0
        }
        function onRowsRemoved(parent, first, last) {
            mainWindow.hasFiles = cryptocam.fileListModel.rowCount() > 0
        }
    }
}
