package me.nettee.pancake.core.record;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
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
	public void testCreate() {
		RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
		recordFile.close();
	}

	@Test
	public void testOpen() {
		RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
		recordFile.close();
		RecordFile recordFile2 = RecordFile.open(file);
		recordFile2.close();
	}

	private void testInsertRecord(int rounds) {
		RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
		for (int i = 0; i < rounds; i++) {
			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE - 1) + " ";
			byte[] str = str0.getBytes();
			recordFile.insertRecord(str);
		}
		recordFile.close();
	}

	@Test
	public void testInsertRecordOnce() {
		testInsertRecord(1);
	}

	@Test
	public void testInsertRecordsInOnePage() {
		int rounds = RandomUtils.nextInt(2, Page.DATA_SIZE / RECORD_SIZE);
		testInsertRecord(rounds);
	}

	@Test
	public void testInsertRecordsInMultiplePages() {
		int rounds = RandomUtils.nextInt(Page.DATA_SIZE / RECORD_SIZE + 1, Page.DATA_SIZE * 20 / RECORD_SIZE);
		testInsertRecord(rounds);
	}

	private void testGetRecord(int rounds) {
		RecordFile rf = RecordFile.create(file, RECORD_SIZE);
		Map<RID, String> map = new HashMap<RID, String>();
		for (int i = 0; i < rounds; i++) {
			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
			byte[] str = str0.getBytes();
			RID rid = rf.insertRecord(str);
			map.put(rid, str0);
		}
		for (Entry<RID, String> entry : map.entrySet()) {
			RID rid = entry.getKey();
			String str0 = entry.getValue();
			byte[] rec = rf.getRecord(rid);
			String rec0 = new String(rec, StandardCharsets.US_ASCII);
			assertEquals(str0, rec0);
		}
		rf.close();
	}

	@Test
	public void testGetRecordOnce() {
		testGetRecord(1);
	}
	
	@Test
	public void testGetRecordsInOnePage() {
		int rounds = RandomUtils.nextInt(2, Page.DATA_SIZE / RECORD_SIZE);
		testGetRecord(rounds);
	}

	@Test
	public void testGetRecordsInMultiplePages() {
		int rounds = RandomUtils.nextInt(Page.DATA_SIZE / RECORD_SIZE + 1, Page.DATA_SIZE * 20 / RECORD_SIZE);
		testGetRecord(rounds);
	}

}
