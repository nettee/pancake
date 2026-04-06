use super::{FileHeader, PfError, PfFile, PfFileState, Result, validate_path};
use std::cell::RefCell;
use std::collections::HashMap;
use std::fs::{OpenOptions, remove_file};
use std::io::Write;
use std::path::Path;
use std::rc::Rc;

#[derive(Debug, Default, Clone, Copy)]
pub struct PfManager;

impl PfManager {
    pub const fn new() -> Self {
        Self
    }

    pub fn create_file(&self, path: impl AsRef<Path>) -> Result<()> {
        let path = path.as_ref();
        validate_path(path)?;

        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(path)
            .map_err(|err| match err.kind() {
                std::io::ErrorKind::AlreadyExists => PfError::FileExists,
                std::io::ErrorKind::NotFound => PfError::FileNotFound,
                _ => PfError::Io(err),
            })?;

        let header = FileHeader::new();
        file.write_all(&header.encode())?;
        file.sync_all()?;
        Ok(())
    }

    pub fn destroy_file(&self, path: impl AsRef<Path>) -> Result<()> {
        let path = path.as_ref();
        validate_path(path)?;

        remove_file(path).map_err(|err| match err.kind() {
            std::io::ErrorKind::NotFound => PfError::FileNotFound,
            _ => PfError::Io(err),
        })
    }

    pub fn open_file(&self, path: impl AsRef<Path>) -> Result<PfFile> {
        let path = path.as_ref();
        validate_path(path)?;

        let file = OpenOptions::new()
            .read(true)
            .write(true)
            .open(path)
            .map_err(|err| match err.kind() {
                std::io::ErrorKind::NotFound => PfError::FileNotFound,
                _ => PfError::Io(err),
            })?;

        let header = FileHeader::read_from(&file)?;
        Ok(PfFile {
            inner: Rc::new(RefCell::new(PfFileState {
                file,
                header,
                pin_counts: HashMap::new(),
                live_write_guards: 0,
            })),
        })
    }
}
