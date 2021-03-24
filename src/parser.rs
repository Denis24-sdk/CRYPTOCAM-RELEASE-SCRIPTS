use anyhow::{bail, Result};
use bytes::{ByteOrder, LittleEndian};
use std::io::Read;

use crate::keyring::KeyDigest;

#[derive(Debug)]
pub struct CryptocamFileHeader {
    pub version: u16,
    pub recipient_digests: Vec<KeyDigest>,
}

/// Parses the first (unencrypted) header of a cryptocam output file,
/// which contains the public key digests of the file's recipients.
/// Returns the parsed header and the number of bytes read from the reader
pub fn parse_header(reader: &mut dyn Read) -> Result<(CryptocamFileHeader, u64)> {
    let mut header: [u8; 7] = [0; 7];
    match reader.read_exact(&mut header) {
        Err(_) => bail!("Not a Cryptocam file"),
        _ => (),
    };
    if header[0..4] != [0x1c, 0x5a, 0x8e, 0x9f] {
        bail!("Not a Cryptocam file");
    }
    let version: u16 = LittleEndian::read_u16(&header[4..6]);
    let num_recipients: u8 = header[6];

    let mut read: u64 = header.len() as u64;
    let mut recipient_digests: Vec<KeyDigest> = Vec::new();
    let mut hash_buf: KeyDigest = [0; 16];
    for _ in 0..num_recipients {
        match reader.read_exact(&mut hash_buf) {
            Err(_) => bail!("Not a Cryptocam file"),
            _ => (),
        }
        read += hash_buf.len() as u64;
        recipient_digests.push(hash_buf.clone())
    }

    let cfh = CryptocamFileHeader {
        version,
        recipient_digests,
    };
    Ok((cfh, read))
}
