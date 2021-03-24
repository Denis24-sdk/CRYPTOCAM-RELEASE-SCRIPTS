use crate::keyring::DisplayIdentity;
use anyhow::{Context, Result};
use qrcode::QrCode;
use urlencoding;

pub fn make_qr_code(identity: &DisplayIdentity) -> Result<QrCode> {
    let intent_uri = format!(
        "cryptocam://import_key?key_name={}&public_key={}",
        urlencoding::encode(&identity.name),
        identity.public_key
    );
    QrCode::new(intent_uri).context("Could not create qr code")
}
