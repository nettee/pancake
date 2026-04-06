use super::PfFileState;
use crate::common::PageId;
use std::cell::RefCell;
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
/// This is the RAII boundary: callers mutate page bytes through the guard,
/// and releasing the guard hands dirty bytes back to the buffer pool.
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
        if self.dirty {
            // Dirty bytes stay in the buffer pool until an explicit flush or
            // an LRU eviction writes them back.
            inner.store_dirty_snapshot(self.page_id, std::mem::take(&mut self.data));
        }
        inner.unpin_write(self.page_id);
    }
}
