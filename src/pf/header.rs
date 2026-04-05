use super::{
    read_u32_le, write_u32_into, PfError, Result, HEADER_FREE_LIST_OFFSET, HEADER_MAGIC_OFFSET,
    HEADER_PAGE_COUNT_OFFSET, HEADER_PAGE_SIZE_OFFSET, HEADER_VERSION_OFFSET, PF_HEADER_SIZE,
    PF_MAGIC, PF_PAGE_SIZE, PF_VERSION,
};
use crate::common::PageId;
use std::fs::File;
use std::io::{Read, Seek, SeekFrom, Write};

#[derive(Debug, Clone, Copy)]
pub(crate) struct FileHeader {
    // Number of currently allocated data pages in the file.
    pub(crate) page_count: PageId,
}

impl FileHeader {
    pub(crate) fn new() -> Self {
        Self { page_count: 0 }
    }

    pub(crate) fn encode(&self) -> [u8; PF_HEADER_SIZE] {
        let mut bytes = [0u8; PF_HEADER_SIZE];
        // The header is encoded explicitly as bytes so the persistent format is
        // deterministic and does not depend on Rust struct layout.
        bytes[HEADER_MAGIC_OFFSET..HEADER_MAGIC_OFFSET + PF_MAGIC.len()].copy_from_slice(&PF_MAGIC);
        write_u32_into(&mut bytes, HEADER_VERSION_OFFSET, PF_VERSION)
            .expect("header version write");
        write_u32_into(&mut bytes, HEADER_PAGE_SIZE_OFFSET, PF_PAGE_SIZE as u32)
            .expect("header page size write");
        write_u32_into(&mut bytes, HEADER_PAGE_COUNT_OFFSET, self.page_count)
            .expect("header page count write");
        write_u32_into(&mut bytes, HEADER_FREE_LIST_OFFSET, super::INVALID_PAGE_ID)
            .expect("header free list write");
        bytes
    }

    pub(crate) fn read_from(file: &File) -> Result<Self> {
        let mut bytes = [0u8; PF_HEADER_SIZE];
        let mut file = file.try_clone().map_err(|_| PfError::InvalidFile)?;
        file.seek(SeekFrom::Start(0))
            .map_err(|_| PfError::InvalidFile)?;
        file.read_exact(&mut bytes)
            .map_err(|_| PfError::InvalidFile)?;

        if bytes[HEADER_MAGIC_OFFSET..HEADER_MAGIC_OFFSET + PF_MAGIC.len()] != PF_MAGIC {
            return Err(PfError::InvalidFile);
        }

        let version =
            read_u32_le(&bytes, HEADER_VERSION_OFFSET).map_err(|_| PfError::InvalidFile)?;
        let page_size =
            read_u32_le(&bytes, HEADER_PAGE_SIZE_OFFSET).map_err(|_| PfError::InvalidFile)?;
        if version != PF_VERSION || page_size != PF_PAGE_SIZE as u32 {
            return Err(PfError::InvalidFile);
        }

        // Phase 1 only consumes page_count, but it still validates the free-list
        // field so later phases can extend the header without changing the file
        // validation path.
        let page_count =
            read_u32_le(&bytes, HEADER_PAGE_COUNT_OFFSET).map_err(|_| PfError::InvalidFile)?;
        let _free_list =
            read_u32_le(&bytes, HEADER_FREE_LIST_OFFSET).map_err(|_| PfError::InvalidFile)?;
        Ok(Self { page_count })
    }

    pub(crate) fn write_to(&self, file: &mut File) -> Result<()> {
        let bytes = self.encode();
        file.seek(SeekFrom::Start(0))?;
        file.write_all(&bytes)?;
        Ok(())
    }
}
