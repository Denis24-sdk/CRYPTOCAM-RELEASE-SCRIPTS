#[macro_use]
extern crate cstr;
extern crate qmetaobject;
use cpp::cpp;
use qmetaobject::*;
use std::env;

mod config;
mod cryptocam_companion;
mod list_items;
use cryptocam_companion::CryptocamCompanion;

cpp! {{
    #include<QtQuickControls2/QQuickStyle>
    #include<QtCore/QString>
}}

qrc!(qml_resources,
    "qml" {
        "qml/main.qml" as "main.qml",
        "qml/MainWindow.qml" as "MainWindow.qml",
        "qml/KeyWindow.qml" as "KeyWindow.qml",
        "qml/CreateKeyWindow.qml" as "CreateKeyWindow.qml",
    }
);

fn main() {
    if env::var("QT_QUICK_CONTROLS_STYLE").is_err() {
        unsafe {
            cpp!([]  {
                QQuickStyle::setStyle(QString("fusion"));
            })
        };
    }
    qml_register_type::<CryptocamCompanion>(
        cstr!("CryptocamCompanion"),
        1,
        0,
        cstr!("CryptocamCompanion"),
    );
    let mut engine = QmlEngine::new();
    qml_resources();
    engine.load_file("qrc:/qml/main.qml".into());
    engine.exec();
}
