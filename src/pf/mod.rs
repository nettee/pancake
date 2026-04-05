use crate::common::{read_u32_le, write_u32_le, INVALID_PAGE_ID, PAGE_SIZE};
use std::path::Path;

const PF_MAGIC: [u8; 8] = *b"PANCAKE1";
const PF_VERSION: u32 = 1;
// Phase 1 keeps the file header as one full page so the on-disk layout stays
// simple and leaves room for future metadata such as free-list state.
const PF_HEADER_SIZE: usize = PAGE_SIZE;
const PF_PAGE_SIZE: usize = PAGE_SIZE;
const HEADER_MAGIC_OFFSET: usize = 0;
const HEADER_VERSION_OFFSET: usize = 8;
const HEADER_PAGE_SIZE_OFFSET: usize = 12;
const HEADER_PAGE_COUNT_OFFSET: usize = 16;
const HEADER_FREE_LIST_OFFSET: usize = 20;

#[derive(Debug)]
pub enum PfError {
    InvalidName,
    FileExists,
    FileNotFound,
    InvalidFile,
    InvalidPageId,
    OutstandingWriteGuard,
    Io(std::io::Error),
}

impl core::fmt::Display for PfError {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        match self {
            Self::InvalidName => write!(f, "invalid file name"),
            Self::FileExists => write!(f, "file already exists"),
            Self::FileNotFound => write!(f, "file not found"),
            Self::InvalidFile => write!(f, "invalid paged file"),
            Self::InvalidPageId => write!(f, "invalid page id"),
            Self::OutstandingWriteGuard => write!(f, "outstanding write guard"),
            Self::Io(err) => write!(f, "io error: {err}"),
        }
    }
}

impl std::error::Error for PfError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::Io(err) => Some(err),
            _ => None,
        }
    }
}

impl From<std::io::Error> for PfError {
    fn from(value: std::io::Error) -> Self {
        Self::Io(value)
    }
}

pub type Result<T> = core::result::Result<T, PfError>;

mod file;
mod header;
mod manager;
mod page;

pub use file::PfFile;
pub use manager::PfManager;
pub use page::{ReadPageGuard, WritePageGuard};

#[allow(unused_imports)]
pub(crate) use file::{page_offset, read_page_bytes, write_page_bytes, zero_page, PfFileState};
pub(crate) use header::FileHeader;

fn validate_path(path: &Path) -> Result<()> {
    if path.as_os_str().is_empty() {
        return Err(PfError::InvalidName);
    }
    Ok(())
}

fn write_u32_into(bytes: &mut [u8], offset: usize, value: u32) -> crate::common::Result<()> {
    write_u32_le(bytes, offset, value)
}

#[cfg(test)]
mod tests;
