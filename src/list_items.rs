extern crate qmetaobject;
use libcryptocam::{
    key_qrcode::make_qr_code,
    keyring::{DisplayIdentity, KeyDigest},
    qrcode::render::svg,
};
use qmetaobject::*;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Default, SimpleListItem)]
#[allow(non_snake_case)]
pub struct KeyItem {
    pub fingerprint: String,
    pub name: String,
    pub publicKey: String,
    pub qrCode: String,
    path: PathBuf, //needs to be private as it's not a QMetaType field
    key_digest: KeyDigest,
}

impl KeyItem {
    pub fn path(&self) -> &Path {
        self.path.as_path()
    }
}

impl QMetaType for KeyItem {}

impl From<&DisplayIdentity> for KeyItem {
    fn from(identity: &DisplayIdentity) -> Self {
        KeyItem {
            name: identity.name.clone(),
            fingerprint: hex::encode(identity.public_key_digest),
            publicKey: identity.public_key.clone(),
            qrCode: svg_qrcode(identity),
            key_digest: identity.public_key_digest,
            path: identity.path.clone(),
        }
    }
}

#[derive(Debug, Clone)]
pub enum GuiFileStatus {
    Added,
    Processing,
    Error,
    Canceled,
    Done,
}

impl QMetaType for GuiFileStatus {
    const CONVERSION_TO_STRING: Option<fn(&Self) -> QString> = Some(|s: &GuiFileStatus| {
        match s {
            GuiFileStatus::Added => "Added",
            GuiFileStatus::Processing => "Processing",
            GuiFileStatus::Error => "Error",
            GuiFileStatus::Canceled => "Canceled",
            &GuiFileStatus::Done => "Done",
        }
        .to_string()
        .into()
    });
}

impl Default for GuiFileStatus {
    fn default() -> Self {
        GuiFileStatus::Added
    }
}

#[derive(Debug, Clone, Default, SimpleListItem)]
#[allow(non_snake_case)]
pub struct FileItem {
    pub name: String,
    pub status: GuiFileStatus,
    pub error: String,
    pub progressPercent: u8,
}

fn svg_qrcode(identity: &DisplayIdentity) -> String {
    let qrcode = match make_qr_code(identity) {
        Err(e) => return String::new(),
        Ok(q) => q,
    };
    let svg = qrcode.render::<svg::Color>().build();
    svg
}
