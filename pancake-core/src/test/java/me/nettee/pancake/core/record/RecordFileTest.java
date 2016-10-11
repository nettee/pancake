package me.nettee.pancake.core.record;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

public class RecordFileTest {
	
	private File file;

	@Before
	public void setUp() throws Exception {
		file = new File("/tmp/c.db");
		if (file.exists()) {
			file.delete();
		}
	}
	
	@Test
	public void testCreate() throws IOException {
		RecordFile recordFile = RecordFile.create(file, 8);
		recordFile.close();
	}
	
	@Test
	public void testOpen() throws IOException {
		RecordFile recordFile = RecordFile.create(file, 8);
		recordFile.close();
		RecordFile recordFile2 = RecordFile.open(file);
		recordFile2.close();
	}
	
	@Test
	public void testInsertRecord() throws IOException {
		RecordFile recordFile = RecordFile.create(file, 8);
		byte[] str = "abcdefgh".getBytes();
		recordFile.insertRecord(str);
	}

}
