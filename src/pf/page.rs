use super::{write_page_bytes, PfFileState};
use crate::common::PageId;
use std::cell::RefCell;
use std::io::Write;
use std::rc::Rc;

#[derive(Debug, Clone)]
pub struct ReadPageGuard {
    pub(crate) page_id: PageId,
    pub(crate) data: Vec<u8>,
}

impl ReadPageGuard {
    pub fn page_id(&self) -> PageId {
        self.page_id
    }

    pub fn data(&self) -> &[u8] {
        &self.data
    }
}

#[derive(Debug)]
/// A write guard owns a temporary page buffer and persists it on drop.
///
/// This is the Phase 1 RAII boundary: callers mutate page bytes through the
/// guard, and releasing the guard completes the write-back step.
pub struct WritePageGuard {
    pub(crate) page_id: PageId,
    pub(crate) data: Vec<u8>,
    pub(crate) dirty: bool,
    pub(crate) inner: Rc<RefCell<PfFileState>>,
}

impl WritePageGuard {
    pub fn page_id(&self) -> PageId {
        self.page_id
    }

    pub fn data(&self) -> &[u8] {
        &self.data
    }

    pub fn data_mut(&mut self) -> &mut [u8] {
        // Mark the page dirty on first mutable access so Drop can decide
        // whether it must write the page back to disk.
        self.dirty = true;
        &mut self.data
    }
}

impl Drop for WritePageGuard {
    fn drop(&mut self) {
        if !self.dirty {
            return;
        }

        if let Ok(mut inner) = self.inner.try_borrow_mut() {
            // Phase 1 uses direct file-backed writes instead of a buffer pool.
            // Dropping a dirty guard is therefore the moment when its page data
            // is persisted to disk.
            if let Err(err) = write_page_bytes(&mut inner.file, self.page_id, &self.data) {
                panic!("failed to write dirty page {}: {err}", self.page_id);
            }
            if let Err(err) = inner.file.flush() {
                panic!("failed to flush dirty page {}: {err}", self.page_id);
            }
        }
    }
}
