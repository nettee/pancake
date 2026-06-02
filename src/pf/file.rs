use super::{FileHeader, PfError, ReadPageGuard, Result, WritePageGuard};
use crate::common::PageId;
use std::cell::RefCell;
use std::collections::{HashMap, HashSet};
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

        inner.buffer_pool.remove(page_id);
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
        inner.buffer_pool.remove(page_id);
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
            let mut inner = self.inner.borrow_mut();
            let mut file = inner.file.try_clone()?;
            inner.read_page_snapshot(&mut file, page_id)?
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
            let mut inner = self.inner.borrow_mut();
            let mut file = inner.file.try_clone()?;
            inner.read_page_snapshot(&mut file, page_id)?
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
        let mut inner = self.inner.borrow_mut();
        // A live write guard may still own newer bytes than the file itself.
        // Reject flushes in that state so callers do not assume data is durable
        // before the guard has been dropped.
        if inner.live_write_guards > 0 {
            return Err(PfError::OutstandingWriteGuard);
        }

        let mut file = inner.file.try_clone()?;
        inner.header.write_to(&mut file)?;
        inner.flush_dirty_pages(&mut file)?;
        file.sync_all()?;
        inner.mark_clean_after_sync();
        Ok(())
    }

    #[cfg(test)]
    pub(crate) fn set_buffer_capacity_for_tests(&self, capacity: usize) {
        self.inner.borrow_mut().buffer_pool.set_capacity(capacity);
    }

    #[cfg(test)]
    pub(crate) fn evicted_pages_for_tests(&self) -> Vec<PageId> {
        self.inner.borrow().buffer_pool.eviction_log.clone()
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
    pub(crate) pin_counts: HashMap<PageId, PinState>,
    pub(crate) live_write_guards: usize,
    pub(crate) buffer_pool: BufferPool,
}

#[derive(Debug, Default, Clone, Copy)]
pub(crate) struct PinState {
    pub(crate) readers: usize,
    pub(crate) writers: usize,
}

#[derive(Debug)]
pub(crate) struct BufferPool {
    capacity: usize,
    tick: u64,
    frames: HashMap<PageId, BufferFrame>,
    #[cfg(test)]
    pub(crate) eviction_log: Vec<PageId>,
}

#[derive(Debug)]
struct BufferFrame {
    data: Vec<u8>,
    dirty: bool,
    last_used: u64,
}

impl BufferPool {
    pub(crate) fn new(capacity: usize) -> Self {
        Self {
            capacity: capacity.max(1),
            tick: 0,
            frames: HashMap::new(),
            #[cfg(test)]
            eviction_log: Vec::new(),
        }
    }

    #[cfg(test)]
    pub(crate) fn set_capacity(&mut self, capacity: usize) {
        self.capacity = capacity.max(1);
    }

    fn bump_tick(&mut self) -> u64 {
        self.tick = self.tick.saturating_add(1);
        self.tick
    }

    fn cached_clone(&mut self, page_id: PageId) -> Option<Vec<u8>> {
        let tick = self.bump_tick();
        let frame = self.frames.get_mut(&page_id)?;
        frame.last_used = tick;
        Some(frame.data.clone())
    }

    fn insert_clean(&mut self, page_id: PageId, data: Vec<u8>) {
        let tick = self.bump_tick();
        self.frames.insert(
            page_id,
            BufferFrame {
                data,
                dirty: false,
                last_used: tick,
            },
        );
    }

    fn store_dirty(&mut self, page_id: PageId, data: Vec<u8>) {
        let tick = self.bump_tick();
        self.frames.insert(
            page_id,
            BufferFrame {
                data,
                dirty: true,
                last_used: tick,
            },
        );
    }

    fn remove(&mut self, page_id: PageId) {
        self.frames.remove(&page_id);
    }

    fn dirty_pages_in_lru_order(&self) -> Vec<PageId> {
        let mut dirty_pages: Vec<(PageId, u64)> = self
            .frames
            .iter()
            .filter(|(_, frame)| frame.dirty)
            .map(|(page_id, frame)| (*page_id, frame.last_used))
            .collect();
        dirty_pages.sort_by_key(|(_, last_used)| *last_used);
        dirty_pages
            .into_iter()
            .map(|(page_id, _)| page_id)
            .collect()
    }

    fn write_dirty_pages(&self, file: &mut File) -> Result<()> {
        for page_id in self.dirty_pages_in_lru_order() {
            if let Some(frame) = self.frames.get(&page_id) {
                write_page_bytes(file, page_id, &frame.data)?;
            }
        }
        file.flush()?;
        Ok(())
    }

    fn mark_clean_after_sync(&mut self) {
        for frame in self.frames.values_mut() {
            frame.dirty = false;
        }
    }

    fn lru_victim(&self, pin_counts: &HashMap<PageId, PinState>) -> Option<PageId> {
        self.frames
            .iter()
            .filter(|(page_id, _)| {
                !pin_counts
                    .get(page_id)
                    .map(|state| state.readers + state.writers > 0)
                    .unwrap_or(false)
            })
            .min_by_key(|(_, frame)| frame.last_used)
            .map(|(page_id, _)| *page_id)
    }

    fn evict(&mut self, file: &mut File, page_id: PageId) -> Result<()> {
        if let Some(frame) = self.frames.get(&page_id) {
            if frame.dirty {
                write_page_bytes(file, page_id, &frame.data)?;
                file.sync_all()?;
            }
        }
        self.frames.remove(&page_id);
        #[cfg(test)]
        self.eviction_log.push(page_id);
        Ok(())
    }
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

    pub(crate) fn read_page_snapshot(
        &mut self,
        file: &mut File,
        page_id: PageId,
    ) -> Result<Vec<u8>> {
        if page_id >= self.header.page_count {
            return Err(PfError::InvalidPageId);
        }

        if let Some(data) = self.buffer_pool.cached_clone(page_id) {
            return Ok(data);
        }

        let data = read_page_bytes(file, &self.header, page_id)?;
        if self.buffer_pool.frames.len() >= self.buffer_pool.capacity {
            let victim = self
                .buffer_pool
                .lru_victim(&self.pin_counts)
                .ok_or(PfError::PagePinned)?;
            self.buffer_pool.evict(file, victim)?;
        }
        self.buffer_pool.insert_clean(page_id, data.clone());
        Ok(data)
    }

    pub(crate) fn store_dirty_snapshot(&mut self, page_id: PageId, data: Vec<u8>) {
        self.buffer_pool.store_dirty(page_id, data);
    }

    pub(crate) fn flush_dirty_pages(&mut self, file: &mut File) -> Result<()> {
        self.buffer_pool.write_dirty_pages(file)
    }

    pub(crate) fn mark_clean_after_sync(&mut self) {
        self.buffer_pool.mark_clean_after_sync();
    }
}

impl Drop for PfFileState {
    fn drop(&mut self) {
        if self.live_write_guards > 0 {
            return;
        }

        if self.buffer_pool.dirty_pages_in_lru_order().is_empty() {
            return;
        }

        let header = self.header;
        let result = (|| -> Result<()> {
            header.write_to(&mut self.file)?;
            self.buffer_pool.write_dirty_pages(&mut self.file)?;
            self.file.sync_all()?;
            self.buffer_pool.mark_clean_after_sync();
            Ok(())
        })();

        result.unwrap_or_else(|err| panic!("failed to flush dirty PF state on drop: {err}"));
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

    // Page reads always materialize an owned buffer in this implementation so
    // guards can stay short-lived without self-referential borrowing.
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
