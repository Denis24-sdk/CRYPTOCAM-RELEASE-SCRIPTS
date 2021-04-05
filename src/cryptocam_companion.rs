extern crate qmetaobject;
use qmetaobject::*;
use threadpool::ThreadPool;

use crate::{
    config,
    list_items::{FileItem, GuiFileStatus, KeyItem},
};
use libcryptocam::{
    decrypt::decrypt,
    decrypt::ProgressCallback as DecryptProgressCallback,
    keyring::{DecryptIdentityError, DecryptionError, DisplayIdentity, Keyring},
};
use std::{
    borrow::{Borrow, BorrowMut},
    cell::RefCell,
    convert::TryInto,
    error::Error,
    fs,
    path::PathBuf,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
};
use url::Url;
use urlencoding;

#[derive(Debug)]
pub enum FileStatus {
    Added,
    Processing(u8),
    Error(String),
    Canceled,
    Done,
}

#[derive(Debug)]
pub struct InputFile {
    path: PathBuf,
    status: FileStatus,
    cancel: Arc<AtomicBool>,
}

impl From<&InputFile> for FileItem {
    fn from(input_file: &InputFile) -> Self {
        let mut progressPercent: u8 = 0;
        let mut error = String::new();
        let status = match &input_file.status {
            FileStatus::Added => GuiFileStatus::Added,
            FileStatus::Done => GuiFileStatus::Done,
            FileStatus::Canceled => GuiFileStatus::Canceled,
            FileStatus::Processing(p) => {
                progressPercent = *p;
                GuiFileStatus::Processing
            }
            FileStatus::Error(e) => {
                error = e.to_string();
                GuiFileStatus::Error
            }
        };
        FileItem {
            name: input_file
                .path
                .file_name()
                .unwrap()
                .to_string_lossy()
                .to_string(),
            progressPercent,
            error,
            status,
        }
    }
}

#[derive(QObject, Default)]
#[allow(non_snake_case)]
pub struct CryptocamCompanion {
    base: qt_base_class!(trait QObject),
    output_path: qt_property!(QString; NOTIFY outputPathChanged ALIAS outputPath WRITE set_output_path),
    outputPathChanged: qt_signal!(),

    keyring_path: qt_property!(QString; NOTIFY keyringPathChanged ALIAS keyringPath WRITE set_keyring_path),
    keyringPathChanged: qt_signal!(),
    keyring_path_exists: qt_property!(bool; NOTIFY keyringPathExistsChanged ALIAS keyringPathExists),
    keyringPathExistsChanged: qt_signal!(),
    keyListModel: qt_property!(RefCell<SimpleListModel<KeyItem>>; NOTIFY keyListModelChanged),
    keyListModelChanged: qt_signal!(),
    createKey:
        qt_method!(fn(&mut self, name: QString, passphrase: QString, passphraseConfirm: QString)),

    fileListModel: qt_property!(RefCell<SimpleListModel<FileItem>>; NOTIFY fileListModelChanged),
    fileListModelChanged: qt_signal!(),
    _files: Vec<InputFile>,
    addFiles: qt_method!(fn(&mut self, urls: QString)),
    removeFile: qt_method!(fn(&mut self, index: usize)),

    askPassphrase: qt_signal!(identity_name: QString, error: QString),
    passphraseAsked: qt_method!(fn(&mut self, passphrase: QString)),

    error: qt_property!(QString; NOTIFY errorChanged),
    errorChanged: qt_signal!(),
    decryptClicked: qt_method!(fn(&mut self)),
    init: qt_method!(fn(&mut self)),
    keyring: Option<Keyring>,
    _keyring_path: Option<PathBuf>,
    threadpool: ThreadPool,
    cancel: Vec<Arc<AtomicBool>>,

    waiting_for_decrypt_identities: Vec<DisplayIdentity>,
}

#[allow(non_snake_case)]
impl CryptocamCompanion {
    fn init(&mut self) {
        let keyring_path = config::keyring_path();
        self.keyring_path = keyring_path.to_string_lossy().to_string().into();
        self._keyring_path = Some(keyring_path);
        self.keyringPathChanged();
        self.load_keyring();
        self.threadpool.set_num_threads(8);
    }

    fn load_keyring(&mut self) {
        let keyring_path = self._keyring_path.as_ref().unwrap();
        let exists = keyring_path.exists();
        self.keyring_path_exists = exists;
        self.keyringPathExistsChanged();
        if !exists {
            return;
        }
        self.keyring = match Keyring::load_from_directory(keyring_path.clone()) {
            Err(e) => {
                self.error = e.to_string().into();
                self.errorChanged();
                None
            }
            Ok(keyring) => {
                let mut keylist = self.keyListModel.borrow_mut();
                keylist.reset_data(
                    keyring
                        .display_identities()
                        .iter()
                        .map(|i| i.into())
                        .collect(),
                );
                Some(keyring)
            }
        };
    }

    fn set_keyring_path(&mut self, url: QString) {
        match Url::parse(url.to_string().as_str()) {
            Err(e) => {
                self.error = e.to_string().into();
                self.errorChanged();
                return;
            }
            Ok(url) => {
                let path = PathBuf::from(urlencoding::decode(url.path()).unwrap());
                self.keyring_path_exists = path.exists();
                self.keyringPathExistsChanged();
                match config::set_keyring_path(path.as_path()) {
                    Err(e) => {
                        println!("{}", e);
                        self.error = format!("Error writing to config file: {}", e).into();
                        self.errorChanged();
                    }
                    Ok(()) => {}
                };
                self.keyring_path = path.to_string_lossy().to_string().into();
                self._keyring_path = Some(path);
                self.load_keyring();
                // the qr code view updates when this signal is emitted so the new data
                // should already be available when it's emitted
                self.keyringPathChanged();
            }
        };
    }

    fn set_output_path(&mut self, output_path: QString) {
        let url = Url::parse(output_path.to_string().as_str()).unwrap();
        let path: QString = url.path().into();
        self.output_path = path;
        self.outputPathChanged();
    }

    fn createKey(&mut self, name: QString, passphrase: QString, passphraseConfirm: QString) {
        assert!(passphrase == passphraseConfirm);
        let passphrase = passphrase.to_string();
        let name = name.to_string();
        match &self.keyring {
            None => {
                if let Some(path) = &self._keyring_path {
                    if !path.exists() {
                        match fs::create_dir(&path) {
                            Err(e) => {
                                self.error =
                                    format!("Could not create keyring directory: {}", e).into();
                                self.errorChanged();
                                return;
                            }
                            Ok(()) => {}
                        };
                    }
                    match Keyring::load_from_directory(path.clone()) {
                        Err(e) => {
                            self.error = format!("Could not load keyring: {}", e).into();
                            self.errorChanged();
                            return;
                        }
                        Ok(k) => {
                            self.keyring = Some(k);
                        }
                    }
                } else {
                    self.error = "Could not create key: no keyring opened".into();
                    self.errorChanged();
                    return;
                }
            }
            Some(k) => {}
        };
        if let Some(keyring) = &mut self.keyring {
            match keyring.create_key(&name, Some(&passphrase)) {
                Err(e) => {
                    self.error = format!("Could not create key: {}", e).into();
                    self.errorChanged();
                }
                Ok(identity) => {
                    self.keyListModel.borrow_mut().push((&identity).into());
                }
            }
        } else {
            unreachable!()
        }
        self.keyringPathExistsChanged();
    }

    // we get the urls separated by spaces here because I can't for the life of me figure
    // out how to pass a string array from qml to c++/rust with QVariantList and all that
    fn addFiles(&mut self, urls: QString) {
        self._files.retain(|f| match f.status {
            FileStatus::Done | FileStatus::Canceled | FileStatus::Error(_) => false,
            _ => true,
        });
        let urls = urls.to_string();
        let urls: Vec<&str> = urls.split(' ').collect();
        for url in urls {
            let url = Url::parse(url.to_string().as_str()).unwrap();
            let path = urlencoding::decode(url.path()).unwrap();
            let path = PathBuf::from(path);
            if path.is_file() {
                self._files.push(InputFile {
                    path,
                    status: FileStatus::Added,
                    cancel: Arc::new(AtomicBool::from(false)),
                });
            } else if path.is_dir() {
                let read_dir = match fs::read_dir(&path) {
                    Err(e) => {
                        self.error =
                            format!("Error reading directory {}: {}", path.to_string_lossy(), e)
                                .into();
                        self.errorChanged();
                        continue;
                    }
                    Ok(r) => r,
                };
                for entry in read_dir {
                    let entry = match entry {
                        Err(e) => {
                            println!("Error reading dir entry: {}", e);
                            continue;
                        }
                        Ok(e) => e,
                    };
                    if entry.path().is_file() {
                        self._files.push(InputFile {
                            path: entry.path(),
                            status: FileStatus::Added,
                            cancel: Arc::new(AtomicBool::from(false)),
                        });
                    }
                }
            }
        }
        // clearing out the model because calling reset_data(new_data) doesn't seem to do anything
        let num_rows = self.fileListModel.borrow().row_count();
        for _ in 0..num_rows {
            self.fileListModel.borrow_mut().remove(0);
        }
        for item in self._files.iter().map(|f| FileItem::from(f)) {
            self.fileListModel.borrow_mut().push(item);
        }
    }

    fn decryptClicked(&mut self) {
        let out_path = PathBuf::from(self.output_path.to_string());
        let input_files: Vec<(usize, PathBuf, Arc<AtomicBool>)> = self
            ._files
            .iter()
            .enumerate() // save original index in file list
            .filter(|(i, f)| match f.status {
                FileStatus::Added | FileStatus::Error(_) => true,
                _ => false,
            })
            .map(|(i, f)| (i, f.path.clone(), f.cancel.clone()))
            .collect();
        for (index, input_path, cancel) in input_files {
            let mut keyring = match &mut self.keyring {
                None => {
                    self.error = "Error: No keyring opened!".to_string().into();
                    self.errorChanged();
                    return;
                }
                Some(k) => k,
            };
            let file = match fs::File::open(input_path.clone()) {
                Err(e) => {
                    let status = FileStatus::Error(e.to_string());
                    self.set_file_status(index, status);
                    continue;
                }
                Ok(f) => f,
            };
            let mut decryption_job = match decrypt(file, &mut keyring, out_path.clone()) {
                Err(error) => match error.downcast::<DecryptionError>() {
                    Err(other_error) => {
                        let status = FileStatus::Error(other_error.to_string());
                        self.set_file_status(index, status);
                        continue;
                    }
                    Ok(error) => match error {
                        DecryptionError::IdentityEncrypted(identity) => {
                            let name: QString = identity.name.clone().into();
                            self.waiting_for_decrypt_identities.push(identity);
                            self.askPassphrase(name, String::new().into());
                            return;
                        }
                        DecryptionError::NoSuchKey => {
                            let status = FileStatus::Error(DecryptionError::NoSuchKey.to_string());
                            self.set_file_status(index, status);
                            continue;
                        }
                        other => {
                            let status = FileStatus::Error(other.to_string());
                            self.set_file_status(index, status);
                            continue;
                        }
                    },
                },
                Ok(j) => j,
            };
            let qptr = QPointer::from(&*self);
            let _set_file_status = queued_callback(move |(index, status): (usize, FileStatus)| {
                let _self = qptr.as_pinned().map(|_self| {
                    _self.borrow_mut().set_file_status(index, status);
                });
            });
            self.threadpool.execute(move || {
                struct ProgressCallback {
                    offset: u64,
                    total_file_size: u64,
                    file_index: usize,
                    set_file_status: Box<dyn Fn((usize, FileStatus))>,
                };
                impl DecryptProgressCallback for ProgressCallback {
                    fn set_offset(&mut self, offset: u64) {
                        self.offset = offset;
                    }

                    fn set_total_file_size(&mut self, n: u64) {
                        self.total_file_size = n;
                    }

                    fn on_error(&mut self, error: Box<dyn Error>) {
                        (*self.set_file_status)((
                            self.file_index,
                            FileStatus::Error(error.to_string()),
                        ));
                    }

                    fn on_progress(&mut self, processed_bytes: u64) {
                        let percent = (self.offset + processed_bytes) * 100 / self.total_file_size;
                        (*self.set_file_status)((
                            self.file_index,
                            FileStatus::Processing(percent as u8),
                        ));
                    }

                    fn on_complete(&mut self) {
                        (*self.set_file_status)((self.file_index, FileStatus::Done));
                    }
                }
                let mut progress_callback = ProgressCallback {
                    offset: 0,
                    total_file_size: 0,
                    set_file_status: Box::new(_set_file_status),
                    file_index: index,
                };
                decryption_job.run(Box::new(&mut progress_callback), cancel);
            })
        }
    }

    fn removeFile(&mut self, index: usize) {
        match self._files[index].status {
            FileStatus::Added => {
                self.fileListModel.borrow_mut().remove(index);
                self._files.remove(index);
            }
            FileStatus::Processing(_) => {
                self._files[index]
                    .borrow_mut()
                    .cancel
                    .store(true, Ordering::Relaxed);
                self.set_file_status(index, FileStatus::Canceled);
            }
            _ => {}
        };
    }

    fn passphraseAsked(&mut self, passphrase: QString) {
        let identity_to_decrypt = self.waiting_for_decrypt_identities.last().unwrap();
        match self.keyring.as_mut().unwrap().decrypt_identity(
            &identity_to_decrypt.public_key_digest,
            passphrase.to_string(),
        ) {
            Err(error) => match error {
                DecryptIdentityError::WrongPassphrase => {
                    self.askPassphrase(
                        identity_to_decrypt.name.clone().into(),
                        "Wrong passphrase".to_string().into(),
                    );
                }
                DecryptIdentityError::Other(e) => {
                    self.askPassphrase(
                        identity_to_decrypt.name.clone().into(),
                        e.to_string().into(),
                    );
                    return;
                }
            },
            Ok(()) => {
                self.waiting_for_decrypt_identities
                    .remove(self.waiting_for_decrypt_identities.len() - 1);
                self.decryptClicked();
            }
        };
    }

    fn set_file_status(&mut self, index: usize, status: FileStatus) {
        let file: &mut InputFile = self._files.get_mut(index).unwrap();
        if let FileStatus::Canceled = file.status {
            // once it's cancelled, remaining callbacks from the worker threads are ignored
            return;
        }
        let error = match status {
            FileStatus::Error(ref e) => (&e).to_string(),
            _ => String::new(),
        };
        let progress = match status {
            FileStatus::Processing(p) => p,
            _ => 0,
        };
        let gui_status = match status {
            FileStatus::Processing(_) => GuiFileStatus::Processing,
            FileStatus::Error(_) => GuiFileStatus::Error,
            FileStatus::Added => GuiFileStatus::Added,
            FileStatus::Done => GuiFileStatus::Done,
            FileStatus::Canceled => GuiFileStatus::Canceled,
        };
        self.fileListModel.borrow_mut().change_line(
            index,
            FileItem {
                name: file.path.file_name().unwrap().to_string_lossy().into(),
                status: gui_status,
                progressPercent: progress,
                error,
            },
        );
        file.status = status;
    }
}
