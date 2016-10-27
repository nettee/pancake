package me.nettee.pancake.core.record;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.NotImplementedException;

import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;

public class RecordFile {

	private PagedFile file;

	private Metadata metadata;

	// private Map<Integer, RecordPage> buffer;
	//
	private RecordFile(PagedFile file) throws IOException {
		this.file = file;
		metadata = new Metadata();
		// buffer = new TreeMap<>();
	}

	public static RecordFile create(File file, int recordSize) {
		if (recordSize < 4) {
			throw new NotImplementedException("record size less than 4 is currently not suppported");
		}

		try {
			PagedFile pagedFile = PagedFile.create(file);

			// /*
			// * Record file structure:
			// *
			// * 1. The first page is reserved for header page. Data page starts
			// * from number 1.
			// *
			// * 2. In each data page, record are stored in a compact way. The
			// * last 2 bytes of every data page is reserved to store deleted
			// * record slot index.
			// *
			// * 3. The length of record should be at least 2 bytes. If not,
			// each
			// * record is padded to 2 bytes. This is convenient for finding
			// * deleted record slots.
			// */
			//
			RecordFile recordFile = new RecordFile(pagedFile);
			recordFile.metadata.recordSize = recordSize;
			recordFile.metadata.dataPageStartingNum = 1;
			recordFile.metadata.numOfRecords = 0;
			recordFile.metadata.numOfPages = 1;
			recordFile.metadata.numOfRecordsInOnePage = (Page.DATA_SIZE - RecordPage.HEADER_SIZE) / recordSize;
			recordFile.metadata.firstFreePage = Metadata.NO_FREE_PAGE;

			if (pagedFile.getNumOfPages() != 0) {
				throw new RecordFileException("created paged file is not empty");
			}
			Page page = pagedFile.allocatePage();
			recordFile.metadata.write(page.getData());
			return recordFile;
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

	public static RecordFile open(File file) {
		try {
			PagedFile pagedFile = PagedFile.open(file);

			if (pagedFile.getNumOfPages() == 0) {
				throw new RecordFileException("opened paged file is empty");
			}
			Page page = pagedFile.getFirstPage();

			RecordFile recordFile = new RecordFile(pagedFile);
			recordFile.metadata.read(page.getData());
			return recordFile;
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

	public void close() {
		try {
			file.forceAllPages();
			file.close();
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

	private RecordPage getFreeRecordPage() {
		if (metadata.firstFreePage == Metadata.NO_FREE_PAGE) {
			try {
				Page page = file.allocatePage();
				metadata.firstFreePage = page.getNum();
				return RecordPage.create(page, metadata.recordSize);
			} catch (IOException e) {
				throw new RecordFileException(e);
			}
		} else {
			return getRecordPage(metadata.firstFreePage);
		}

	}

	private RecordPage getRecordPage(int pageNum) {
		try {
			Page page = file.getPage(pageNum);
			return RecordPage.open(page);
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

	/**
	 * Insert <tt>data</tt> as a new record in file.
	 * 
	 * @param data
	 *            record data
	 * @return record identifier <tt>RID</tt>
	 */
	public RID insertRecord(byte[] data) {
		RecordPage recordPage = getFreeRecordPage();
		int insertedPageNum = recordPage.getPage().getNum();
		int insertedSlotNum = recordPage.insert(data);
		recordPage.force();
		metadata.numOfRecords += 1;
		return new RID(insertedPageNum, insertedSlotNum);
	}

	public byte[] getRecord(RID rid) {
		RecordPage recordPage = getRecordPage(rid.pageNum);
		byte[] record = recordPage.get(rid.slotNum);
		recordPage.force();
		return record;
	}

	public void updateRecord(RID rid, byte[] data) {
		RecordPage recordPage = getRecordPage(rid.pageNum);
		recordPage.update(rid.slotNum, data);
		recordPage.force();
	}

	public void deleteRecord(RID rid) {
		RecordPage recordPage = getRecordPage(rid.pageNum);
		recordPage.delete(rid.slotNum);
		recordPage.force();
		metadata.numOfRecords -= 1;
	}

	// /**
	// * Scan over all the records in this file.
	// *
	// * @return an <tt>Iterator</tt> to iterate through records
	// */
	// public Iterator<byte[]> scan() {
	// return new RecordIterator();
	// }
	//
	// /**
	// * Scan over the records in this file that satisfies predicate
	// * <tt>pred</tt>.
	// *
	// * @param pred
	// * predicate on record data
	// * @return an <tt>Iterator</tt> to iterate through records
	// */
	// public Iterator<byte[]> scan(Predicate<byte[]> pred) {
	// return new RecordIterator(pred);
	// }
	//
	// private class RecordIterator implements Iterator<byte[]> {
	//
	// private Predicate<byte[]> pred;
	// private RID nextRid;
	//
	// RecordIterator() {
	// this(null);
	// }
	//
	// RecordIterator(Predicate<byte[]> pred) {
	// this.pred = pred;
	// nextRid = firstRid();
	// search();
	// }
	//
	// /*
	// * Move nextRid to the first record that satisfies predicate. If current
	// * nextRid already satisfies, do not move.
	// */
	// private void search() {
	// if (pred == null) {
	// return;
	// }
	// while (metadata.ridRecordNumber(nextRid) < metadata.numOfRecords &&
	// !pred.test(getRecord(nextRid))) {
	// nextRid = nextRid(nextRid);
	// }
	// }
	//
	// public boolean hasNext() {
	// return metadata.ridRecordNumber(nextRid) < metadata.numOfRecords;
	// }
	//
	// public byte[] next() {
	// byte[] data = getRecord(nextRid);
	// nextRid = nextRid(nextRid);
	// search();
	// return data;
	// }
	//
	// }
	//
	// void ttt() {
	// try {
	// Page page = file.getPage(1);
	// new RecordPage(page, metadata);
	// } catch (IOException e) {
	// throw new AssertionError(e);
	// }
	//
	// }

}
