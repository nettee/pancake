use super::*;
use crate::common::PAGE_SIZE;
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

fn temp_path(name: &str) -> PathBuf {
    let stamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("time went backwards")
        .as_nanos();
    std::env::temp_dir().join(format!("pancake_pf_{name}_{stamp}.pf"))
}

fn cleanup(path: &Path) {
    let _ = fs::remove_file(path);
}

#[test]
fn create_open_and_destroy_file() {
    let mgr = PfManager::new();
    let path = temp_path("lifecycle");
    cleanup(&path);

    mgr.create_file(&path).expect("create file");
    let file = mgr.open_file(&path).expect("open file");
    file.flush_all().expect("flush file");
    drop(file);
    mgr.destroy_file(&path).expect("destroy file");

    assert!(!path.exists());
}

#[test]
fn create_existing_file_fails() {
    let mgr = PfManager::new();
    let path = temp_path("create_exists");
    cleanup(&path);

    mgr.create_file(&path).expect("create file");
    let err = mgr
        .create_file(&path)
        .expect_err("create existing should fail");
    assert!(matches!(err, PfError::FileExists));

    cleanup(&path);
}

#[test]
fn open_missing_or_invalid_file_fails() {
    let mgr = PfManager::new();
    let missing = temp_path("missing");
    cleanup(&missing);
    let err = mgr.open_file(&missing).expect_err("missing should fail");
    assert!(matches!(err, PfError::FileNotFound));

    let invalid = temp_path("invalid");
    cleanup(&invalid);
    {
        let mut file = File::create(&invalid).expect("create invalid file");
        file.write_all(b"not a pf file").expect("write invalid");
    }
    let err = mgr.open_file(&invalid).expect_err("invalid should fail");
    assert!(matches!(err, PfError::InvalidFile));

    cleanup(&invalid);
}

#[test]
fn destroy_missing_file_fails() {
    let mgr = PfManager::new();
    let path = temp_path("destroy_missing");
    cleanup(&path);

    let err = mgr
        .destroy_file(&path)
        .expect_err("destroy missing should fail");
    assert!(matches!(err, PfError::FileNotFound));
}

#[test]
fn allocate_page_returns_zeroed_page() {
    let mgr = PfManager::new();
    let path = temp_path("allocate_zeroed");
    cleanup(&path);

    mgr.create_file(&path).expect("create file");
    let file = mgr.open_file(&path).expect("open file");

    let page_id = file.allocate_page().expect("allocate page");
    assert_eq!(page_id, 0);

    let page = file.get_page(page_id).expect("read page");
    assert_eq!(page.page_id(), page_id);
    assert!(page.data().iter().all(|&byte| byte == 0));

    drop(page);
    drop(file);
    cleanup(&path);
}

#[test]
fn write_page_persists_across_reopen() {
    let mgr = PfManager::new();
    let path = temp_path("persist");
    cleanup(&path);

    mgr.create_file(&path).expect("create file");
    let file = mgr.open_file(&path).expect("open file");

    let page_id = file.allocate_page().expect("allocate page");
    {
        let mut page = file.get_page_mut(page_id).expect("get mutable page");
        page.data_mut()[0..4].copy_from_slice(&[1, 2, 3, 4]);
        page.data_mut()[10] = 99;
    }
    file.flush_all().expect("flush all");
    drop(file);

    let reopened = mgr.open_file(&path).expect("reopen file");
    let page = reopened.get_page(page_id).expect("read page after reopen");
    assert_eq!(&page.data()[0..4], &[1, 2, 3, 4]);
    assert_eq!(page.data()[10], 99);

    drop(page);
    drop(reopened);
    cleanup(&path);
}

#[test]
fn forward_scan_skips_disposed_pages() {
    let mgr = PfManager::new();
    let path = temp_path("forward_scan");
    cleanup(&path);

    mgr.create_file(&path).expect("create file");
    let file = mgr.open_file(&path).expect("open file");

    let first = file.allocate_page().expect("allocate first");
    let middle = file.allocate_page().expect("allocate middle");
    let last = file.allocate_page().expect("allocate last");

    assert_eq!(first, 0);
    assert_eq!(middle, 1);
    assert_eq!(last, 2);

    file.dispose_page(middle).expect("dispose middle");

    assert_eq!(file.first_page_id().expect("first page"), first);
    assert_eq!(file.next_page_id(first).expect("next page"), last);
    let err = file.next_page_id(last).expect_err("scan should end");
    assert!(matches!(err, PfError::EndOfFile));

    drop(file);
    cleanup(&path);
}

#[test]
fn dispose_page_reuses_lifo_order() {
    let mgr = PfManager::new();
    let path = temp_path("dispose_lifo");
    cleanup(&path);

    mgr.create_file(&path).expect("create file");
    let file = mgr.open_file(&path).expect("open file");

    let first = file.allocate_page().expect("allocate first");
    let second = file.allocate_page().expect("allocate second");
    let third = file.allocate_page().expect("allocate third");

    file.dispose_page(second).expect("dispose second");
    file.dispose_page(third).expect("dispose third");

    let reused_first = file.allocate_page().expect("reuse most recent");
    let reused_second = file.allocate_page().expect("reuse next");

    assert_eq!(reused_first, third);
    assert_eq!(reused_second, second);

    let page = file.get_page(reused_first).expect("read reused page");
    assert!(page.data().iter().all(|&byte| byte == 0));

    assert_eq!(first, 0);
    drop(page);
    drop(file);
    cleanup(&path);
}

#[test]
fn live_page_guard_blocks_dispose_until_drop() {
    let mgr = PfManager::new();
    let path = temp_path("guard_pin");
    cleanup(&path);

    mgr.create_file(&path).expect("create file");
    let file = mgr.open_file(&path).expect("open file");

    let page_id = file.allocate_page().expect("allocate page");
    let guard = file.get_page(page_id).expect("get page");

    let err = file
        .dispose_page(page_id)
        .expect_err("dispose should fail while pinned");
    assert!(matches!(err, PfError::PagePinned));

    drop(guard);
    file.dispose_page(page_id).expect("dispose after drop");

    drop(file);
    cleanup(&path);
}

#[test]
fn incompatible_page_guards_are_rejected_but_multiple_reads_are_allowed() {
    let mgr = PfManager::new();
    let path = temp_path("guard_aliasing");
    cleanup(&path);

    mgr.create_file(&path).expect("create file");
    let file = mgr.open_file(&path).expect("open file");

    let page_id = file.allocate_page().expect("allocate page");

    let read_guard = file.get_page(page_id).expect("get read guard");
    let second_read_guard = file.get_page(page_id).expect("get second read guard");
    assert_eq!(read_guard.data(), second_read_guard.data());

    let err = file
        .get_page_mut(page_id)
        .expect_err("write guard should be rejected while reads are live");
    assert!(matches!(err, PfError::PagePinned));

    drop(second_read_guard);
    drop(read_guard);

    let write_guard = file.get_page_mut(page_id).expect("get write guard");

    let err = file
        .get_page(page_id)
        .expect_err("read guard should be rejected while write is live");
    assert!(matches!(err, PfError::PagePinned));

    let err = file
        .get_page_mut(page_id)
        .expect_err("second write guard should be rejected while write is live");
    assert!(matches!(err, PfError::PagePinned));

    drop(write_guard);
    drop(file);
    cleanup(&path);
}

#[test]
fn flush_all_rejects_live_write_guard() {
    let mgr = PfManager::new();
    let path = temp_path("flush_live_guard");
    cleanup(&path);

    mgr.create_file(&path).expect("create file");
    let file = mgr.open_file(&path).expect("open file");
    let page_id = file.allocate_page().expect("allocate page");
    let _guard = file.get_page_mut(page_id).expect("get mutable page");

    let err = file
        .flush_all()
        .expect_err("flush should fail with live guard");
    assert!(matches!(err, PfError::OutstandingWriteGuard));

    drop(file);
    cleanup(&path);
}

#[test]
fn open_rejects_plain_file_with_correct_size() {
    let mgr = PfManager::new();
    let path = temp_path("plain_file");
    cleanup(&path);

    {
        let mut file = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .open(&path)
            .expect("create plain file");
        file.set_len(PAGE_SIZE as u64).expect("set size");
        file.flush().expect("flush");
    }

    let err = mgr.open_file(&path).expect_err("plain file should fail");
    assert!(matches!(err, PfError::InvalidFile));

    cleanup(&path);
}
