/*
A file in a keyring directory looks like this:
Filename: anything
File contents:
{
"name": string,
"public_key": string,
"identity_type": "unencrypted"/"scrypt_encrypted",
"encrypted_identity": string, //base64 encoded
"identity": string
}
*/

use age;
use anyhow::{anyhow, bail, Context, Result};
use base64;
use ini::Ini;
use log::warn;
use secrecy::{ExposeSecret, Secret};
use sha2::{Digest, Sha256};
use std::{
    collections::HashMap,
    convert::TryInto,
    error::Error,
    format,
    io::{Read, Write},
    iter,
    path::{Path, PathBuf},
    str::FromStr,
};
use thiserror::Error;

pub type KeyDigest = [u8; 16];

pub struct Keyring {
    path: PathBuf,
    identities: HashMap<KeyDigest, Identity>,
}

#[derive(Debug, Clone)]
pub struct DisplayIdentity {
    pub path: PathBuf,
    pub name: String,
    pub public_key: String,
    pub public_key_digest: KeyDigest,
}

#[derive(Debug, Error)]
pub enum DecryptionError {
    #[error("Identity {0:?} is encrypted")]
    IdentityEncrypted(DisplayIdentity),
    #[error("No key found to decrypt file")]
    NoSuchKey,
    #[error("Decrytion error: {0:?}")]
    Other(anyhow::Error),
}

impl From<anyhow::Error> for DecryptionError {
    fn from(e: anyhow::Error) -> Self {
        DecryptionError::Other(e)
    }
}

#[derive(Debug, Error)]
pub enum DecryptIdentityError {
    #[error("Wrong passphrase")]
    WrongPassphrase,
    #[error("Error decrypting secret key: {0:?}")]
    Other(anyhow::Error),
}

impl From<anyhow::Error> for DecryptIdentityError {
    fn from(e: anyhow::Error) -> Self {
        DecryptIdentityError::Other(e)
    }
}

impl Keyring {
    pub fn load_from_directory(keyring_path: PathBuf) -> Result<Keyring> {
        let entries = std::fs::read_dir(&keyring_path)?;
        let mut identities: HashMap<KeyDigest, Identity> = HashMap::new();
        for result in entries {
            let entry = match result {
                Err(e) => {
                    warn!("{}", e);
                    continue;
                }
                Ok(e) => e,
            };
            let file_type = match entry.file_type() {
                Err(e) => {
                    warn!("{}", e);
                    continue;
                }
                Ok(t) => t,
            };
            if file_type.is_dir() {
                continue;
            }
            match parse_keyring_file(&entry) {
                Err(e) => {
                    warn!("{}", e)
                }
                Ok(identity) => {
                    identities.insert(identity.public_key_digest, identity);
                }
            };
        }
        Ok(Keyring {
            path: keyring_path,
            identities,
        })
    }

    pub fn create_key(
        &mut self,
        name: &str,
        passphrase: Option<&str>,
    ) -> Result<DisplayIdentity, Box<dyn Error>> {
        let age_identity = age::x25519::Identity::generate();
        let public_key = age_identity.to_public().to_string();
        let secret_key = match passphrase {
            None => SecretKey::Unencrypted(age_identity),
            Some(passphrase) => {
                let encrypted = encrypt_identity(
                    age_identity.to_string().expose_secret(),
                    passphrase.to_owned(),
                )?;
                SecretKey::ScryptEncrypted(encrypted)
            }
        };

        let ini_secret_key: String = match &secret_key {
            SecretKey::Unencrypted(k) => k.to_string().expose_secret().to_string(),
            SecretKey::ScryptEncrypted(k) => base64::encode(&k),
        };
        let identity_type = match passphrase {
            None => "unencrypted",
            Some(_) => "scrypt_encrypted",
        };
        let mut ini_file = Ini::new();
        ini_file
            .with_section::<String>(None)
            .set("name", name)
            .set("public_key", &public_key)
            .set("identity_type", identity_type)
            .set("secret_key", ini_secret_key);
        let mut keyfile_path = PathBuf::from(&self.path);
        let filename: String = name
            .chars()
            .map(|c| match c {
                ' ' | '/' | '.' => '_',
                other => other,
            })
            .collect();
        keyfile_path.push(Path::new(&format!("{}.ini", &filename)));
        ini_file.write_to_file(&keyfile_path)?;
        let digest = compute_digest(&public_key);
        self.identities.insert(
            digest,
            Identity {
                name: name.to_owned(),
                path: keyfile_path.clone(),
                public_key: public_key.clone(),
                public_key_digest: digest,
                secret_key,
            },
        );
        Ok(DisplayIdentity {
            name: name.to_owned(),
            path: keyfile_path,
            public_key: public_key,
            public_key_digest: digest,
        })
    }

    pub fn display_identities(&self) -> Vec<DisplayIdentity> {
        let mut display_identities: Vec<DisplayIdentity> = self
            .identities
            .values()
            .map(|identity| identity.to_display_identity())
            .collect();
        display_identities.sort_by(|d1, d2| d1.name.cmp(&d2.name));
        display_identities
    }

    pub fn get_identity(&self, digest: &KeyDigest) -> Result<DisplayIdentity> {
        self.identities
            .get(digest)
            .map(|identity| identity.to_display_identity())
            .ok_or_else(|| anyhow!("Key not found"))
    }

    pub fn decrypt(
        &mut self,
        encrypted: impl Read,
        recipient_digests: &Vec<KeyDigest>,
    ) -> std::result::Result<impl Read, DecryptionError> {
        if let Some(digest) = recipient_digests
            .iter()
            .find(|&d| self.identities.contains_key(d))
        {
            let identity = self.identities.get(digest).unwrap();
            let age_identity = match &identity.secret_key {
                SecretKey::ScryptEncrypted(_) => {
                    return Err(DecryptionError::IdentityEncrypted(
                        identity.to_display_identity(),
                    ));
                }
                SecretKey::Unencrypted(identity) => identity,
            };
            let decryptor = match age::Decryptor::new(encrypted) {
                Ok(age::Decryptor::Recipients(d)) => d,
                _ => {
                    return Err(DecryptionError::Other(anyhow!(
                        "Failed to decrypt: not an X25519 Recipient"
                    )))
                }
            };
            decryptor
                .decrypt(iter::once(
                    Box::new(age_identity.clone()) as Box<dyn age::Identity>
                ))
                .map_err(|e| DecryptionError::Other(anyhow!("Failed to decrypt ciphertext: {}", e)))
        } else {
            Err(DecryptionError::NoSuchKey)
        }
    }

    pub fn decrypt_identity(
        &mut self,
        key_digest: &KeyDigest,
        passphrase: String,
    ) -> Result<(), DecryptIdentityError> {
        let identity = self.identities.remove(key_digest).unwrap();
        let encrypted = match &identity.secret_key {
            SecretKey::Unencrypted(_) => {
                self.identities.insert(*key_digest, identity);
                return Ok(());
            }
            SecretKey::ScryptEncrypted(encrypted) => encrypted,
        };
        let age_identity = match try_decrypt_identity(&encrypted, passphrase) {
            Err(e) => {
                self.identities.insert(key_digest.clone(), identity);
                return Err(e);
            }
            Ok(i) => i,
        };
        let identity = Identity {
            secret_key: SecretKey::Unencrypted(age_identity),
            ..identity
        };

        self.identities.insert(*key_digest, identity);
        Ok(())
    }
}

enum SecretKey {
    Unencrypted(age::x25519::Identity),
    ScryptEncrypted(Vec<u8>),
}

struct Identity {
    pub path: PathBuf,
    pub name: String,
    pub public_key: String,
    pub public_key_digest: KeyDigest,
    pub secret_key: SecretKey,
}

impl Identity {
    fn to_display_identity(&self) -> DisplayIdentity {
        DisplayIdentity {
            name: self.name.clone(),
            public_key: self.public_key.clone(),
            public_key_digest: self.public_key_digest.clone(),
            path: self.path.clone(),
        }
    }
}

fn parse_keyring_file(dir_entry: &std::fs::DirEntry) -> Result<Identity> {
    let path = dir_entry.path();
    let ini_file = Ini::load_from_file(&path)?;
    let section = ini_file
        .section::<String>(None)
        .ok_or(anyhow!("Error parsing keyring file"))?;
    let name = section.get("name").ok_or(anyhow!("Missing field name"))?;
    let identity_type = section
        .get("identity_type")
        .ok_or(anyhow!("Missing field identity_type"))?;
    let public_key = section
        .get("public_key")
        .ok_or(anyhow!("Missing field public_key"))?;
    if let Err(_) = age::x25519::Recipient::from_str(public_key) {
        bail!("Invalid public key {}", public_key);
    };
    let secret_key = section
        .get("secret_key")
        .ok_or(anyhow!("Missing field secret_key"))?;
    let secret_key = match identity_type {
        "unencrypted" => match age::x25519::Identity::from_str(&secret_key) {
            Err(e) => bail!("Error parsing secret key: {}", e),
            Ok(age_identity) => SecretKey::Unencrypted(age_identity),
        },
        "scrypt_encrypted" => match base64::decode(&secret_key) {
            Err(_) => bail!("Invalid base64 encoded encrypted identity"),
            Ok(bytes) => SecretKey::ScryptEncrypted(bytes),
        },
        other => bail!("Invalid identity type {}", other),
    };
    let public_key_digest: KeyDigest = compute_digest(&public_key);
    Ok(Identity {
        path,
        name: name.to_string(),
        secret_key,
        public_key_digest,
        public_key: public_key.to_string(),
    })
}

fn encrypt_identity(secret_key: &str, passphrase: String) -> Result<Vec<u8>> {
    let encryptor = age::Encryptor::with_user_passphrase(Secret::new(passphrase));
    let mut encrypted = Vec::<u8>::new();
    let mut writer = match encryptor.wrap_output(&mut encrypted) {
        Err(e) => {
            bail!("Error creating keyfile: {}", e)
        }
        Ok(w) => w,
    };
    writer.write_all(secret_key.as_bytes())?;
    writer.finish()?;
    Ok(encrypted)
}

fn try_decrypt_identity(
    encrypted: &Vec<u8>,
    passphrase: String,
) -> Result<age::x25519::Identity, DecryptIdentityError> {
    let decryptor = match age::Decryptor::new(encrypted.as_slice()) {
        Err(_) => {
            return Err(DecryptIdentityError::Other(anyhow!(
                "Encrypted identity is not a valid age ciphertext. Your keyfile may be corrupt."
            )));
        }
        Ok(d) => match d {
            age::Decryptor::Passphrase(d) => d,
            _ => {
                return Err(DecryptIdentityError::Other(anyhow!(
                    "Encrypted secret key is invalid"
                )))
            }
        },
    };
    let mut decrypted = vec![];
    let mut reader = match decryptor.decrypt(&Secret::new(passphrase), None) {
        Err(_) => return Err(DecryptIdentityError::WrongPassphrase),
        Ok(r) => r,
    };
    reader
        .read_to_end(&mut decrypted)
        .context("Error decrypting secret key")?;
    let identity_str = String::from_utf8(decrypted).context("Invalid UTF-8 in secret key")?;
    age::x25519::Identity::from_str(identity_str.as_str())
        .map_err(|_| DecryptIdentityError::Other(anyhow!("Invalid secret key")))
}

fn compute_digest(public_key: &str) -> KeyDigest {
    let mut digest = Sha256::default();
    digest.update(public_key.as_bytes());
    digest.finalize().to_vec().as_slice()[16..32]
        .try_into()
        .unwrap()
}
