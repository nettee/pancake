package me.nettee.pancake.core.record;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import me.nettee.pancake.core.page.Page;

public class RecordFileTest {

	private static final int RECORD_SIZE = 8;
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
		RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
		recordFile.close();
	}

	@Test
	public void testOpen() throws IOException {
		RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
		recordFile.close();
		RecordFile recordFile2 = RecordFile.open(file);
		recordFile2.close();
	}

	@Test
	public void testInsertRecord() {
		RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
		byte[] str = RandomStringUtils.randomAlphabetic(RECORD_SIZE).getBytes();
		recordFile.insertRecord(str);
		recordFile.close();
	}

	@Test
	public void testInsertRecordsInOnePage() throws IOException {
		RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
		int rounds = RandomUtils.nextInt(2, Page.DATA_SIZE / RECORD_SIZE);
		for (int i = 0; i < rounds; i++) {
			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE - 1) + " ";
			byte[] str = str0.getBytes();
			recordFile.insertRecord(str);
		}
		recordFile.close();
	}
	
	@Test
	public void testInsertRecordsInMultiplePages() throws IOException {
		RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
		int rounds = RandomUtils.nextInt(Page.DATA_SIZE / RECORD_SIZE + 1, Page.DATA_SIZE * 20 / RECORD_SIZE);
		for (int i = 0; i < rounds; i++) {
			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE - 1) + " ";
			byte[] str = str0.getBytes();
			recordFile.insertRecord(str);
		}
		recordFile.close();
	}
	
	@Test
	public void testGetRecord() {
		
	}

}
