package me.nettee.pancake.core.record;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import me.nettee.pancake.core.page.PagedFile;

public class RecordFile {
	
	private PagedFile file;
	private int recordSize;

	public RecordFile(PagedFile file, int recordSize) {
		this.file = file;
		this.recordSize = recordSize;
	}
	
	private boolean hasRecordMagic(byte[] data) {
		byte[] magic = "RECFILE".getBytes(StandardCharsets.US_ASCII);
		return Arrays.copyOf(data, magic.length).equals(magic);
	}
	
	public void insertRecord(byte[] record) {
		
	}

}
