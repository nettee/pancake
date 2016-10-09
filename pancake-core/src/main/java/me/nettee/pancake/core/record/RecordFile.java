package me.nettee.pancake.core.record;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;

public class RecordFile {
	
	private PagedFile file;
	private Metadata metadata = new Metadata();
	
	private byte[] magic = "REC-FILE".getBytes(StandardCharsets.US_ASCII);
	
	private class Metadata {
		int recordSize;
		
		void writeTo(byte[] dest) {
			System.arraycopy(magic, 0, dest, 0, magic.length);
			byte[] recordSizeBB = ByteBuffer.allocate(4).
					order(ByteOrder.BIG_ENDIAN).putInt(recordSize).array();
			System.arraycopy(recordSizeBB, 0, dest, 8, 4);
		}
	}

	public RecordFile(PagedFile file, int recordSize) throws IOException {
		this.file = file;
		this.metadata.recordSize = recordSize;
		initMetadata();
	}
	
	private void initMetadata() throws IOException {
		
		if (file.getNumOfPages() == 0) {
			file.allocatePage();
		}
		
		Page page = file.getFirstPage();
		if (!hasMagic(page.getData())) {
			byte[] data = page.getData();
			metadata.writeTo(data);
			
			file.forcePage(page.getNum());
		}
	}
	
	private boolean hasMagic(byte[] data) {
		return Arrays.copyOf(data, magic.length).equals(magic);
	}
	
	public void insertRecord(byte[] record) {
		
	}

}
