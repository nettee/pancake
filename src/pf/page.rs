use super::{write_page_bytes, PfFileState};
use crate::common::PageId;
use std::cell::RefCell;
use std::io::Write;
use std::rc::Rc;

#[derive(Debug)]
pub struct ReadPageGuard {
    pub(crate) page_id: PageId,
    pub(crate) data: Vec<u8>,
    pub(crate) inner: Rc<RefCell<PfFileState>>,
}

impl ReadPageGuard {
    pub fn page_id(&self) -> PageId {
        self.page_id
    }

    pub fn data(&self) -> &[u8] {
        &self.data
    }
}

impl Drop for ReadPageGuard {
    fn drop(&mut self) {
        let mut inner = self.inner.borrow_mut();
        inner.unpin_read(self.page_id);
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
        let mut inner = self.inner.borrow_mut();
        let write_result = if self.dirty {
            // Phase 2 still uses direct file-backed writes instead of a buffer pool.
            // Dropping a dirty guard is therefore the moment when its page data
            // is persisted to disk.
            if let Err(err) = write_page_bytes(&mut inner.file, self.page_id, &self.data) {
                Err(format!(
                    "failed to write dirty page {}: {err}",
                    self.page_id
                ))
            } else if let Err(err) = inner.file.flush() {
                Err(format!(
                    "failed to flush dirty page {}: {err}",
                    self.page_id
                ))
            } else {
                Ok(())
            }
        } else {
            Ok(())
        };
        inner.unpin_write(self.page_id);
        if let Err(message) = write_result {
            panic!("{message}");
        }
    }
}
