package me.nettee.pancake.core.record;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;

public class RecordFile {

	private PagedFile file;

	private byte[] magic = "REC-FILE".getBytes(StandardCharsets.US_ASCII);

	private int recordSize;
	private int numOfRecords;
	private int numOfPages;
	private int dataPageStartingNum;

	private RecordFile(PagedFile file) throws IOException {
		this.file = file;
	}
	
	public static RecordFile create(PagedFile file, int recordSize) throws IOException {
		RecordFile recordFile = new RecordFile(file);
		recordFile.recordSize = recordSize;
		recordFile.numOfRecords = 0;
		recordFile.numOfPages = 1;
		recordFile.dataPageStartingNum = 1;
		
		if (file.getNumOfPages() == 0) {
			file.allocatePage();
		}
		Page page = file.getFirstPage();
		recordFile.writeMetadata(page.getData());
		file.forcePage(page.getNum());
		return recordFile;
	}
	
	public static RecordFile open(PagedFile file) throws IOException, AssertionError {
		if (file.getNumOfPages() == 0) {
			throw new AssertionError();
		}
		Page page = file.getFirstPage();
		
		RecordFile recordFile = new RecordFile(file);
		recordFile.readMetadata(page.getData());
		return recordFile;
	}
	
	private void readMetadata(byte[] src) throws IOException {
		ByteArrayInputStream bais = new ByteArrayInputStream(src);
		DataInputStream is = new DataInputStream(bais);
		byte[] magic0 = new byte[magic.length];
		is.read(magic0);
		if (!magic0.equals(magic)) {
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
		os.write(magic);
		os.writeInt(recordSize);
		os.writeInt(numOfRecords);
		os.writeInt(numOfPages);
		os.writeInt(dataPageStartingNum);
		byte[] data = baos.toByteArray();
		System.arraycopy(data, 0, dest, 0, data.length);
	}

	private boolean hasMagic(byte[] data) {
		return Arrays.copyOf(data, magic.length).equals(magic);
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
