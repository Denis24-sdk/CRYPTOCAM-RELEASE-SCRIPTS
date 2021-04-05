use anyhow::{anyhow, Result};
use dirs_next;
use ini::Ini;
use std::{
    error::Error,
    path::{Path, PathBuf},
    str::FromStr,
};

const DEFAULT_KEYRING_NAME: &str = "CryptocamKeyring";
const CONFIG_DIR_NAME: &str = "CryptocamCompanion";

fn default_keyring_location() -> PathBuf {
    if cfg!(target_os = "windows") {
        dirs_next::document_dir().expect("Error getting Documents directory")
    } else {
        dirs_next::home_dir().expect("Error getting home directory")
    }
}

fn config_file_path() -> Option<PathBuf> {
    let mut config_dir = dirs_next::config_dir()?;
    config_dir.push(CONFIG_DIR_NAME);
    Some(config_dir)
}

pub fn set_keyring_path(path: &Path) -> Result<()> {
    let config_file_path = config_file_path()
        .ok_or_else(|| anyhow!("Could not read config at {}", path.to_string_lossy()))?;
    let mut config =
        Ini::load_from_file(&config_file_path).or(Ok::<Ini, ini::Error>(Ini::new()))?;
    let section = config
        .with_section(None::<String>)
        .set("keyring_path", path.to_string_lossy());
    config.write_to_file(&config_file_path)?;
    Ok(())
}

/// Returns the keyring path to be used, whether it exists or not.
/// Attempts to read from the config file first, otherwise returns a
/// default value.
pub fn keyring_path() -> PathBuf {
    let from_config: Option<PathBuf> = match config_file_path() {
        None => None,
        Some(config_file_path) => {
            let config = match Ini::load_from_file(&config_file_path) {
                Err(e) => None,
                Ok(c) => Some(c),
            };
            if let Some(config) = config {
                println!("Found config file in {:#?}", config_file_path);
                let keyring_path = config
                    .section::<String>(None)
                    .map(|s| s.get("keyring_path"))
                    .flatten();
                keyring_path.map(|p| PathBuf::from_str(p).ok()).flatten()
            } else {
                // failed to load config file
                None
            }
        }
    };
    if let Some(from_config) = from_config {
        return from_config;
    } else {
        println!("Did not find config. Getting default keyring path");
        return default_keyring();
    }
}

fn default_keyring() -> PathBuf {
    let mut p = default_keyring_location();
    p.push(DEFAULT_KEYRING_NAME);
    println!("Default keyring path: {:#?}", p);
    p
}
