use super::{FileHeader, PfError, ReadPageGuard, Result, WritePageGuard};
use crate::common::PageId;
use std::cell::RefCell;
use std::fs::File;
use std::io::{Read, Seek, SeekFrom, Write};
use std::rc::Rc;

#[derive(Debug)]
pub struct PfFile {
    // Shared file state lets PfFile and page guards coordinate access without
    // building a self-referential structure.
    pub(crate) inner: Rc<RefCell<PfFileState>>,
}

impl PfFile {
    pub fn allocate_page(&self) -> Result<PageId> {
        let mut inner = self.inner.borrow_mut();
        let page_id = inner.header.page_count;
        let new_count = page_id.checked_add(1).ok_or(PfError::InvalidFile)?;
        let offset = page_offset(page_id)?;
        let mut file = inner.file.try_clone()?;

        file.seek(SeekFrom::Start(offset))?;
        file.write_all(&zero_page())?;
        inner.header.page_count = new_count;
        let header = inner.header;
        header.write_to(&mut file)?;
        file.flush()?;
        Ok(page_id)
    }

    pub fn get_page(&self, page_id: PageId) -> Result<ReadPageGuard> {
        let inner = self.inner.borrow();
        let page_count = inner.header.page_count;
        let mut file = inner.file.try_clone()?;
        let data = read_page_bytes(&mut file, page_count, page_id)?;
        Ok(ReadPageGuard { page_id, data })
    }

    pub fn get_page_mut(&self, page_id: PageId) -> Result<WritePageGuard> {
        let inner = self.inner.borrow();
        let page_count = inner.header.page_count;
        let mut file = inner.file.try_clone()?;
        let data = read_page_bytes(&mut file, page_count, page_id)?;
        Ok(WritePageGuard {
            page_id,
            data,
            dirty: false,
            inner: Rc::clone(&self.inner),
        })
    }

    pub fn flush_all(&self) -> Result<()> {
        // A live write guard may still own newer bytes than the file itself.
        // Reject flushes in that state so callers do not assume data is durable
        // before the guard has been dropped.
        if Rc::strong_count(&self.inner) > 1 {
            return Err(PfError::OutstandingWriteGuard);
        }

        let inner = self.inner.borrow();
        let header = inner.header;
        let mut file = inner.file.try_clone()?;
        header.write_to(&mut file)?;
        file.sync_all()?;
        Ok(())
    }
}

#[derive(Debug)]
pub(crate) struct PfFileState {
    pub(crate) file: File,
    pub(crate) header: FileHeader,
}

pub(crate) fn zero_page() -> [u8; super::PF_PAGE_SIZE] {
    [0u8; super::PF_PAGE_SIZE]
}

pub(crate) fn page_offset(page_id: PageId) -> Result<u64> {
    // Data pages start immediately after the fixed-size header page.
    let data_offset = super::PF_HEADER_SIZE as u64;
    let page_span = super::PF_PAGE_SIZE as u64;
    let page_index = u64::from(page_id);
    data_offset
        .checked_add(
            page_index
                .checked_mul(page_span)
                .ok_or(PfError::InvalidFile)?,
        )
        .ok_or(PfError::InvalidFile)
}

pub(crate) fn read_page_bytes(
    file: &mut File,
    page_count: PageId,
    page_id: PageId,
) -> Result<Vec<u8>> {
    if page_id >= page_count {
        return Err(PfError::InvalidPageId);
    }

    // Page reads always materialize an owned buffer in Phase 1, which keeps
    // lifetimes simple until a real buffer pool is introduced.
    let mut data = vec![0u8; super::PF_PAGE_SIZE];
    file.seek(SeekFrom::Start(page_offset(page_id)?))?;
    file.read_exact(&mut data)?;
    Ok(data)
}

pub(crate) fn write_page_bytes(file: &mut File, page_id: PageId, data: &[u8]) -> Result<()> {
    if data.len() != super::PF_PAGE_SIZE {
        return Err(PfError::InvalidFile);
    }

    file.seek(SeekFrom::Start(page_offset(page_id)?))?;
    file.write_all(data)?;
    Ok(())
}
