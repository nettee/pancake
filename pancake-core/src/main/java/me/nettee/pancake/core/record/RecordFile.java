package me.nettee.pancake.core.record;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
		try {
			PagedFile pagedFile = PagedFile.create(file);

			RecordFile recordFile = new RecordFile(pagedFile);
			recordFile.recordSize = recordSize;
			recordFile.dataPageStartingNum = 1;
			recordFile.numOfRecords = 0;
			recordFile.numOfPages = 1;
			recordFile.numOfRecordsInOnePage = Page.DATA_SIZE / recordSize;

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
				file.allocatePage();
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
		checkRID(rid);
		byte[] record = new byte[recordSize];
		try {
			Page page = file.getPage(rid.pageNum);
			System.arraycopy(page.getData(), rid.slotNum * recordSize, record, 0, recordSize);
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
		return record;
	}

	private void checkRID(RID rid) {
		if ((rid.pageNum - dataPageStartingNum) * numOfRecordsInOnePage + rid.slotNum >= numOfRecords) {
			throw new RecordFileException("RID index out of bound");
		}
	}
	
	public void updateRecord(RID rid, byte[] data) {
		checkRID(rid);
		try {
			Page page = file.getPage(rid.pageNum);
			System.arraycopy(data, 0, page.getData(), rid.slotNum * recordSize, recordSize);
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}
	
	public void deleteRecord(RID rid) {
		throw new NotImplementedException("not implemented");
	}

}
