package me.nettee.pancake.core.record;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Predicate;

import org.apache.commons.lang3.NotImplementedException;

import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;

public class RecordFile {

	private PagedFile file;

	private static final String MAGIC = "REC-FILE";

	private int recordSize;
	private int dataPageStartingNum;
	private int numOfRecords;
	private int numOfPages;
	private int numOfRecordsInOnePage;

	private RecordFile(PagedFile file) throws IOException {
		this.file = file;
	}

	public static RecordFile create(File file, int recordSize) {
		if (recordSize < 4) {
			throw new NotImplementedException("record size less than 4 is currently not suppported");
		}

		try {
			PagedFile pagedFile = PagedFile.create(file);

			/*
			 * Record file structure:
			 * 
			 * 1. The first page is reserved for header page. Data page starts
			 * from number 1.
			 * 
			 * 2. In each data page, record are stored in a compact way. The
			 * last 2 bytes of every data page is reserved to store deleted
			 * record slot index.
			 * 
			 * 3. The length of record should be at least 2 bytes. If not, each
			 * record is padded to 2 bytes. This is convenient for finding
			 * deleted record slots.
			 */

			RecordFile recordFile = new RecordFile(pagedFile);
			recordFile.recordSize = recordSize;
			recordFile.dataPageStartingNum = 1;
			recordFile.numOfRecords = 0;
			recordFile.numOfPages = 1;
			recordFile.numOfRecordsInOnePage = (Page.DATA_SIZE - 2) / recordSize;

			if (pagedFile.getNumOfPages() == 0) {
				pagedFile.allocatePage();
			}
			Page page = pagedFile.getFirstPage();
			recordFile.writeMetadata(page.getData());
			pagedFile.forcePage(page.getNum());
			return recordFile;
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

	public static RecordFile open(File file) {
		try {
			PagedFile pagedFile = PagedFile.open(file);

			if (pagedFile.getNumOfPages() == 0) {
				throw new AssertionError();
			}
			Page page = pagedFile.getFirstPage();

			RecordFile recordFile = new RecordFile(pagedFile);
			recordFile.readMetadata(page.getData());
			return recordFile;
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

	private void readMetadata(byte[] src) {
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(src);
			DataInputStream is = new DataInputStream(bais);
			byte[] magic0 = new byte[MAGIC.length()];
			is.read(magic0);
			if (!MAGIC.equals(new String(magic0, StandardCharsets.US_ASCII))) {
				throw new RecordFileException("magic does not match");
			}
			recordSize = is.readInt();
			dataPageStartingNum = is.readInt();
			numOfRecords = is.readInt();
			numOfPages = is.readInt();
			numOfRecordsInOnePage = is.readInt();
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

	private void writeMetadata(byte[] dest) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream os = new DataOutputStream(baos);
			os.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
			os.writeInt(recordSize);
			os.writeInt(dataPageStartingNum);
			os.writeInt(numOfRecords);
			os.writeInt(numOfPages);
			os.writeInt(numOfRecordsInOnePage);
			byte[] data = baos.toByteArray();
			System.arraycopy(data, 0, dest, 0, data.length);
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

	/**
	 * Insert <tt>data</tt> as a new record in file.
	 * 
	 * @param data
	 *            record data
	 * @return record identifier <tt>RID</tt>
	 * @throws IOException
	 */
	public RID insertRecord(byte[] data) {

		int insertPageNum = dataPageStartingNum + numOfRecords / numOfRecordsInOnePage;
		int insertSlotNum = numOfRecords % numOfRecordsInOnePage;

		try {
			if (insertSlotNum == 0) {
				Page page = file.allocatePage();
				byte[] ending = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) 0xffff).array();
				System.arraycopy(ending, 0, page.getData(), Page.DATA_SIZE - 2, 2);
			}
			Page page = file.getPage(insertPageNum);
			System.arraycopy(data, 0, page.getData(), insertSlotNum * recordSize, recordSize);
			numOfRecords++;
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
		return new RID(insertPageNum, insertSlotNum);
	}

	public byte[] getRecord(RID rid) {
		checkRidIndexBound(rid);
		byte[] record = new byte[recordSize];
		try {
			Page page = file.getPage(rid.pageNum);
			System.arraycopy(page.getData(), rid.slotNum * recordSize, record, 0, recordSize);
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
		return record;
	}

	private int ridRecordNumber(RID rid) {
		return (rid.pageNum - dataPageStartingNum) * numOfRecordsInOnePage + rid.slotNum;
	}

	private RID firstRid() {
		return new RID(dataPageStartingNum, 0);
	}

	private RID nextRid(RID rid) {
		checkRidIndexBound(rid);
		if (rid.slotNum < numOfRecordsInOnePage - 1) {
			return new RID(rid.pageNum, rid.slotNum + 1);
		} else {
			return new RID(rid.pageNum + 1, 0);
		}
	}

	private void checkRidIndexBound(RID rid) {
		if (ridRecordNumber(rid) >= numOfRecords) {
			throw new RecordFileException("RID index out of bound");
		}
	}

	public void updateRecord(RID rid, byte[] data) {
		checkRidIndexBound(rid);
		try {
			Page page = file.getPage(rid.pageNum);
			System.arraycopy(data, 0, page.getData(), rid.slotNum * recordSize, recordSize);
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

	public void deleteRecord(RID rid) {
		try {
			// Filling 0xdd byte(s) is only for debug use.
			byte[] temp = new byte[recordSize - 2];
			Arrays.fill(temp, (byte) 0xdd);

			Page page = file.getPage(rid.pageNum);

			byte[] ending = Arrays.copyOfRange(page.getData(), Page.DATA_SIZE - 2, Page.DATA_SIZE);
			short nextSlotNum = ByteBuffer.wrap(ending).order(ByteOrder.BIG_ENDIAN).getShort();
			byte[] newData = ByteBuffer.allocate(recordSize).order(ByteOrder.BIG_ENDIAN).putShort(nextSlotNum).put(temp)
					.array();
			System.arraycopy(newData, 0, page.getData(), rid.slotNum * recordSize, recordSize);

			byte[] slotNum = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) rid.slotNum).array();
			System.arraycopy(slotNum, 0, page.getData(), Page.DATA_SIZE - 2, 2);

		} catch (IOException e) {
			throw new RecordFileException(e);
		}
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

		private Predicate<byte[]> pred;
		private RID nextRid;

		RecordIterator() {
			this(null);
		}

		RecordIterator(Predicate<byte[]> pred) {
			this.pred = pred;
			nextRid = firstRid();
			search();
		}

		/*
		 * Move nextRid to the first record that satisfies predicate. If current
		 * nextRid already satisfies, do not move.
		 */
		private void search() {
			if (pred == null) {
				return;
			}
			while (ridRecordNumber(nextRid) < numOfRecords && !pred.test(getRecord(nextRid))) {
				nextRid = nextRid(nextRid);
			}
		}

		public boolean hasNext() {
			return ridRecordNumber(nextRid) < numOfRecords;
		}

		public byte[] next() {
			byte[] data = getRecord(nextRid);
			nextRid = nextRid(nextRid);
			search();
			return data;
		}

	}

}
