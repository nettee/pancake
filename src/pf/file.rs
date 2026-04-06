use super::{FileHeader, PfError, ReadPageGuard, Result, WritePageGuard};
use crate::common::PageId;
use std::cell::RefCell;
use std::collections::HashSet;
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
        let mut file = inner.file.try_clone()?;

        let page_id = if inner.header.free_list != super::INVALID_PAGE_ID {
            let page_id = inner.header.free_list;
            let next_free = read_free_list_next(&mut file, page_id)?;
            inner.header.free_list = next_free;
            page_id
        } else {
            let page_id = inner.header.page_count;
            inner.header.page_count = page_id.checked_add(1).ok_or(PfError::InvalidFile)?;
            page_id
        };

        write_page_bytes(&mut file, page_id, &zero_page())?;
        inner.header.write_to(&mut file)?;
        file.flush()?;
        Ok(page_id)
    }

    pub fn dispose_page(&self, page_id: PageId) -> Result<()> {
        let mut inner = self.inner.borrow_mut();
        ensure_live_page(&inner, page_id)?;
        if inner.is_pinned(page_id) {
            return Err(PfError::PagePinned);
        }
        if inner.is_disposed(page_id)? {
            return Err(PfError::InvalidPageId);
        }

        let mut file = inner.file.try_clone()?;
        let next_free = inner.header.free_list;
        write_u32_link(&mut file, page_id, next_free)?;
        inner.header.free_list = page_id;
        inner.header.write_to(&mut file)?;
        file.flush()?;
        Ok(())
    }

    pub fn first_page_id(&self) -> Result<PageId> {
        let inner = self.inner.borrow();
        let mut file = inner.file.try_clone()?;
        let disposed = collect_disposed_pages(&mut file, &inner.header)?;
        for page_id in 0..inner.header.page_count {
            if !disposed.contains(&page_id) {
                return Ok(page_id);
            }
        }
        Err(PfError::EndOfFile)
    }

    pub fn next_page_id(&self, current: PageId) -> Result<PageId> {
        let inner = self.inner.borrow();
        if current >= inner.header.page_count {
            return Err(PfError::InvalidPageId);
        }

        let mut file = inner.file.try_clone()?;
        let disposed = collect_disposed_pages(&mut file, &inner.header)?;
        if disposed.contains(&current) {
            return Err(PfError::InvalidPageId);
        }

        for page_id in current.saturating_add(1)..inner.header.page_count {
            if !disposed.contains(&page_id) {
                return Ok(page_id);
            }
        }
        Err(PfError::EndOfFile)
    }

    pub fn get_page(&self, page_id: PageId) -> Result<ReadPageGuard> {
        let data = {
            let inner = self.inner.borrow();
            let mut file = inner.file.try_clone()?;
            read_page_bytes(&mut file, &inner.header, page_id)?
        };
        let mut inner = self.inner.borrow_mut();
        if !inner.can_pin_read(page_id) {
            return Err(PfError::PagePinned);
        }
        inner.pin_read(page_id);
        Ok(ReadPageGuard {
            page_id,
            data,
            inner: Rc::clone(&self.inner),
        })
    }

    pub fn get_page_mut(&self, page_id: PageId) -> Result<WritePageGuard> {
        let data = {
            let inner = self.inner.borrow();
            let mut file = inner.file.try_clone()?;
            read_page_bytes(&mut file, &inner.header, page_id)?
        };
        let mut inner = self.inner.borrow_mut();
        if !inner.can_pin_write(page_id) {
            return Err(PfError::PagePinned);
        }
        inner.pin_write(page_id);
        Ok(WritePageGuard {
            page_id,
            data,
            dirty: false,
            inner: Rc::clone(&self.inner),
        })
    }

    pub fn flush_all(&self) -> Result<()> {
        let inner = self.inner.borrow();
        // A live write guard may still own newer bytes than the file itself.
        // Reject flushes in that state so callers do not assume data is durable
        // before the guard has been dropped.
        if inner.live_write_guards > 0 {
            return Err(PfError::OutstandingWriteGuard);
        }

        let header = inner.header;
        let mut file = inner.file.try_clone()?;
        header.write_to(&mut file)?;
        file.sync_all()?;
        Ok(())
    }
}

fn ensure_live_page(inner: &PfFileState, page_id: PageId) -> Result<()> {
    if page_id >= inner.header.page_count {
        return Err(PfError::InvalidPageId);
    }
    Ok(())
}

#[derive(Debug)]
pub(crate) struct PfFileState {
    pub(crate) file: File,
    pub(crate) header: FileHeader,
    pub(crate) pin_counts: std::collections::HashMap<PageId, PinState>,
    pub(crate) live_write_guards: usize,
}

#[derive(Debug, Default, Clone, Copy)]
pub(crate) struct PinState {
    pub(crate) readers: usize,
    pub(crate) writers: usize,
}

impl PfFileState {
    pub(crate) fn can_pin_read(&self, page_id: PageId) -> bool {
        self.pin_counts
            .get(&page_id)
            .map(|state| state.writers == 0)
            .unwrap_or(true)
    }

    pub(crate) fn can_pin_write(&self, page_id: PageId) -> bool {
        self.pin_counts
            .get(&page_id)
            .map(|state| state.readers == 0 && state.writers == 0)
            .unwrap_or(true)
    }

    pub(crate) fn pin_read(&mut self, page_id: PageId) {
        self.pin_counts.entry(page_id).or_default().readers += 1;
    }

    pub(crate) fn pin_write(&mut self, page_id: PageId) {
        let state = self.pin_counts.entry(page_id).or_default();
        state.writers += 1;
        self.live_write_guards += 1;
    }

    pub(crate) fn unpin_read(&mut self, page_id: PageId) {
        if let Some(state) = self.pin_counts.get_mut(&page_id) {
            state.readers = state.readers.saturating_sub(1);
            if state.readers == 0 && state.writers == 0 {
                self.pin_counts.remove(&page_id);
            }
        }
    }

    pub(crate) fn unpin_write(&mut self, page_id: PageId) {
        if let Some(state) = self.pin_counts.get_mut(&page_id) {
            state.writers = state.writers.saturating_sub(1);
            if state.readers == 0 && state.writers == 0 {
                self.pin_counts.remove(&page_id);
            }
        }
        self.live_write_guards = self.live_write_guards.saturating_sub(1);
    }

    pub(crate) fn is_pinned(&self, page_id: PageId) -> bool {
        self.pin_counts
            .get(&page_id)
            .map(|state| state.readers + state.writers > 0)
            .unwrap_or(false)
    }

    pub(crate) fn is_disposed(&mut self, page_id: PageId) -> Result<bool> {
        let mut file = self.file.try_clone()?;
        let disposed = collect_disposed_pages(&mut file, &self.header)?;
        Ok(disposed.contains(&page_id))
    }
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
    header: &FileHeader,
    page_id: PageId,
) -> Result<Vec<u8>> {
    if page_id >= header.page_count {
        return Err(PfError::InvalidPageId);
    }

    let disposed = collect_disposed_pages(file, header)?;
    if disposed.contains(&page_id) {
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

pub(crate) fn write_u32_link(file: &mut File, page_id: PageId, value: PageId) -> Result<()> {
    let mut bytes = zero_page();
    super::write_u32_into(&mut bytes, 0, value).map_err(|_| PfError::InvalidFile)?;
    write_page_bytes(file, page_id, &bytes)
}

fn read_free_list_next(file: &mut File, page_id: PageId) -> Result<PageId> {
    let mut bytes = [0u8; core::mem::size_of::<u32>()];
    file.seek(SeekFrom::Start(page_offset(page_id)?))?;
    file.read_exact(&mut bytes)?;
    Ok(u32::from_le_bytes(bytes))
}

fn collect_disposed_pages(file: &mut File, header: &FileHeader) -> Result<HashSet<PageId>> {
    let mut disposed = HashSet::new();
    let mut current = header.free_list;

    while current != super::INVALID_PAGE_ID {
        if current >= header.page_count {
            return Err(PfError::InvalidFile);
        }
        if !disposed.insert(current) {
            return Err(PfError::InvalidFile);
        }
        current = read_free_list_next(file, current)?;
    }

    Ok(disposed)
}
