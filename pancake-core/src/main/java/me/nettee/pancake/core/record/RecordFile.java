package me.nettee.pancake.core.record;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.apache.commons.lang3.NotImplementedException;

import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;

public class RecordFile {

	private PagedFile file;
	private Metadata metadata;
	private Map<Integer, RecordPage> buffer;

	private RecordFile(PagedFile file) throws IOException {
		this.file = file;
		metadata = new Metadata();
		buffer = new TreeMap<>();
	}

	public static RecordFile create(File file, int recordSize) {
		if (recordSize < 4) {
			throw new NotImplementedException("record size less than 4 is currently not suppported");
		}

		try {
			PagedFile pagedFile = PagedFile.create(file);

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
			for (RecordPage recordPage : buffer.values()) {
				recordPage.persist();
			}
			buffer.clear();
			file.forceAllPages();
			file.close();
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

	private RecordPage getFreeRecordPage() {
		if (metadata.firstFreePage == Metadata.NO_FREE_PAGE) {
			RecordPage recordPage = createRecordPage();
			metadata.firstFreePage = recordPage.getPage().getNum();
			return recordPage;
		} else {
			return getRecordPage(metadata.firstFreePage);
		}

	}
	
	private RecordPage createRecordPage() {
		try {
			Page page = file.allocatePage();
			RecordPage recordPage = RecordPage.create(page, metadata.recordSize);
			buffer.put(recordPage.getPage().getNum(), recordPage);
			return recordPage;
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

	private RecordPage getRecordPage(int pageNum) {
		if (buffer.containsKey(pageNum)) {
			return buffer.get(pageNum);
		}
		try {
			Page page = file.getPage(pageNum);
			RecordPage recordPage = RecordPage.open(page);
			buffer.put(recordPage.getPage().getNum(), recordPage);
			return recordPage;
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
		metadata.numOfRecords += 1;
		return new RID(insertedPageNum, insertedSlotNum);
	}

	public byte[] getRecord(RID rid) {
		RecordPage recordPage = getRecordPage(rid.pageNum);
		byte[] record = recordPage.get(rid.slotNum);
		return record;
	}

	public void updateRecord(RID rid, byte[] data) {
		RecordPage recordPage = getRecordPage(rid.pageNum);
		recordPage.update(rid.slotNum, data);
	}

	public void deleteRecord(RID rid) {
		// TODO add pageNum to firstFreePage
		RecordPage recordPage = getRecordPage(rid.pageNum);
		recordPage.delete(rid.slotNum);
		metadata.numOfRecords -= 1;
	}

	/**
	 * Scan over all the records in this file.
	 *
	 * @return an <tt>Iterator</tt> to iterate through records
	 */
	public Iterator<byte[]> scan() {
		return new RecordIterator();
	}

	/**
	 * Scan over the records in this file that satisfies predicate
	 * <tt>pred</tt>.
	 *
	 * @param pred
	 *            predicate on record data
	 * @return an <tt>Iterator</tt> to iterate through records
	 */
	public Iterator<byte[]> scan(Predicate<byte[]> pred) {
		return new RecordIterator(pred);
	}

	private class RecordIterator implements Iterator<byte[]> {

		private final Predicate<byte[]> pred;
		
		private RecordPage.RecordIterator[] rpis;
		private int current_rpi;
		
		private byte[] nextRecord;

		RecordIterator() {
			this(null);
		}

		RecordIterator(Predicate<byte[]> pred) {
			this.pred = pred;
			int N0 = metadata.dataPageStartingNum;
			int N = metadata.numOfPages;
			rpis = new RecordPage.RecordIterator[N];
			for (int i = N0; i < N; i++) {
				rpis[i] = new RecordPage.RecordIterator(pred);
			}
			current_rpi = 0;
			nextRecord = nextRecord();
		}
		
		private byte[] nextRecord() {
			while (!rpis[current_rpi].hasNext()) {
				current_rpi++;
				if (current_rpi >= metadata.numOfPages) {
					return null;
				}
			}
			return rpis[current_rpi].next();
		}

		public boolean hasNext() {
			return nextRecord != null;
		}

		public byte[] next() {
			byte[] record = nextRecord;
			nextRecord = nextRecord();
			return record;
		}

	}

}
