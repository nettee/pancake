pub type PageId = u32;
pub type SlotId = u16;

pub const PAGE_SIZE: usize = 4096;
pub const INVALID_PAGE_ID: PageId = PageId::MAX;
pub const INVALID_SLOT_ID: SlotId = SlotId::MAX;
pub const DEFAULT_BUFFER_POOL_SIZE: usize = 32;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Rid {
    pub page_id: PageId,
    pub slot_id: SlotId,
}

impl Rid {
    pub const fn new(page_id: PageId, slot_id: SlotId) -> Self {
        Self { page_id, slot_id }
    }

    pub const fn invalid() -> Self {
        Self {
            page_id: INVALID_PAGE_ID,
            slot_id: INVALID_SLOT_ID,
        }
    }

    pub const fn is_invalid(self) -> bool {
        self.page_id == INVALID_PAGE_ID || self.slot_id == INVALID_SLOT_ID
    }
}

#[derive(Debug)]
pub enum Error {
    InvalidData(&'static str),
    OutOfBounds(&'static str),
    Io(std::io::Error),
}

impl Error {
    pub const fn invalid_data(message: &'static str) -> Self {
        Self::InvalidData(message)
    }

    pub const fn out_of_bounds(message: &'static str) -> Self {
        Self::OutOfBounds(message)
    }
}

impl core::fmt::Display for Error {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        match self {
            Self::InvalidData(message) => write!(f, "invalid data: {message}"),
            Self::OutOfBounds(message) => write!(f, "out of bounds: {message}"),
            Self::Io(err) => write!(f, "io error: {err}"),
        }
    }
}

impl std::error::Error for Error {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::Io(err) => Some(err),
            _ => None,
        }
    }
}

impl From<std::io::Error> for Error {
    fn from(value: std::io::Error) -> Self {
        Self::Io(value)
    }
}

pub type Result<T> = core::result::Result<T, Error>;

/// Reads a little-endian `u16` from `bytes` at `offset`.
pub fn read_u16_le(bytes: &[u8], offset: usize) -> Result<u16> {
    let end = offset
        .checked_add(core::mem::size_of::<u16>())
        .ok_or(Error::invalid_data("offset overflow"))?;
    let chunk = bytes
        .get(offset..end)
        .ok_or(Error::out_of_bounds("read_u16_le"))?;
    let mut buf = [0u8; core::mem::size_of::<u16>()];
    buf.copy_from_slice(chunk);
    Ok(u16::from_le_bytes(buf))
}

/// Writes a little-endian `u16` into `bytes` at `offset`.
pub fn write_u16_le(bytes: &mut [u8], offset: usize, value: u16) -> Result<()> {
    let end = offset
        .checked_add(core::mem::size_of::<u16>())
        .ok_or(Error::invalid_data("offset overflow"))?;
    let chunk = bytes
        .get_mut(offset..end)
        .ok_or(Error::out_of_bounds("write_u16_le"))?;
    chunk.copy_from_slice(&value.to_le_bytes());
    Ok(())
}

/// Reads a little-endian `u32` from `bytes` at `offset`.
pub fn read_u32_le(bytes: &[u8], offset: usize) -> Result<u32> {
    let end = offset
        .checked_add(core::mem::size_of::<u32>())
        .ok_or(Error::invalid_data("offset overflow"))?;
    let chunk = bytes
        .get(offset..end)
        .ok_or(Error::out_of_bounds("read_u32_le"))?;
    let mut buf = [0u8; core::mem::size_of::<u32>()];
    buf.copy_from_slice(chunk);
    Ok(u32::from_le_bytes(buf))
}

/// Writes a little-endian `u32` into `bytes` at `offset`.
pub fn write_u32_le(bytes: &mut [u8], offset: usize, value: u32) -> Result<()> {
    let end = offset
        .checked_add(core::mem::size_of::<u32>())
        .ok_or(Error::invalid_data("offset overflow"))?;
    let chunk = bytes
        .get_mut(offset..end)
        .ok_or(Error::out_of_bounds("write_u32_le"))?;
    chunk.copy_from_slice(&value.to_le_bytes());
    Ok(())
}

/// Reads a little-endian `i32` from `bytes` at `offset`.
pub fn read_i32_le(bytes: &[u8], offset: usize) -> Result<i32> {
    let end = offset
        .checked_add(core::mem::size_of::<i32>())
        .ok_or(Error::invalid_data("offset overflow"))?;
    let chunk = bytes
        .get(offset..end)
        .ok_or(Error::out_of_bounds("read_i32_le"))?;
    let mut buf = [0u8; core::mem::size_of::<i32>()];
    buf.copy_from_slice(chunk);
    Ok(i32::from_le_bytes(buf))
}

/// Writes a little-endian `i32` into `bytes` at `offset`.
pub fn write_i32_le(bytes: &mut [u8], offset: usize, value: i32) -> Result<()> {
    let end = offset
        .checked_add(core::mem::size_of::<i32>())
        .ok_or(Error::invalid_data("offset overflow"))?;
    let chunk = bytes
        .get_mut(offset..end)
        .ok_or(Error::out_of_bounds("write_i32_le"))?;
    chunk.copy_from_slice(&value.to_le_bytes());
    Ok(())
}
