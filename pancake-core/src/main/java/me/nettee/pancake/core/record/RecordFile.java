package me.nettee.pancake.core.record;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.log4j.Logger;

import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;

public class RecordFile {

	private PagedFile file;
	
	private static final String MAGIC = "REC-FILE";

	private int recordSize;
	private int numOfRecords;
	private int numOfPages;
	private int dataPageStartingNum;

	private RecordFile(PagedFile file) throws IOException {
		this.file = file;
	}
	
	public static RecordFile create(File file, int recordSize) throws IOException {
		PagedFile pagedFile = PagedFile.create(file);
		
		RecordFile recordFile = new RecordFile(pagedFile);
		recordFile.recordSize = recordSize;
		recordFile.numOfRecords = 0;
		recordFile.numOfPages = 1;
		recordFile.dataPageStartingNum = 1;
		
		if (pagedFile.getNumOfPages() == 0) {
			pagedFile.allocatePage();
		}
		Page page = pagedFile.getFirstPage();
		recordFile.writeMetadata(page.getData());
		pagedFile.forcePage(page.getNum());
		return recordFile;
	}
	
	public static RecordFile open(File file) throws IOException, AssertionError {
		PagedFile pagedFile = PagedFile.open(file);
		
		if (pagedFile.getNumOfPages() == 0) {
			throw new AssertionError();
		}
		Page page = pagedFile.getFirstPage();
		
		RecordFile recordFile = new RecordFile(pagedFile);
		recordFile.readMetadata(page.getData());
		return recordFile;
	}
	
	private void readMetadata(byte[] src) throws IOException {
		ByteArrayInputStream bais = new ByteArrayInputStream(src);
		DataInputStream is = new DataInputStream(bais);
		byte[] magic0 = new byte[MAGIC.length()];
		is.read(magic0);
		if (!MAGIC.equals(new String(magic0, StandardCharsets.US_ASCII))) {
			throw new RecordFileException("magic does not match");
		}
		recordSize = is.readInt();
		numOfRecords = is.readInt();
		numOfPages = is.readInt();
		dataPageStartingNum = is.readInt();
	}

	private void writeMetadata(byte[] dest) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream os = new DataOutputStream(baos);
		os.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
		os.writeInt(recordSize);
		os.writeInt(numOfRecords);
		os.writeInt(numOfPages);
		os.writeInt(dataPageStartingNum);
		byte[] data = baos.toByteArray();
		System.arraycopy(data, 0, dest, 0, data.length);
	}

	public void close() throws IOException {
		file.close();
	}

	public RID insertRecord(byte[] record) {
		int pageNum = 1;
		int slotNum = 0;
		return new RID(pageNum, slotNum);
	}

}
