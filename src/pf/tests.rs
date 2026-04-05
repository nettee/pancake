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
