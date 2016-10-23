package me.nettee.pancake.core.record;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

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

	private abstract class RecordCrudTester {
		abstract void test0(int rounds);

		void test(int rounds) {
			try {
				setUp();
			} catch (Exception e) {
				throw new AssertionError(e);
			}
			test0(rounds);
		}
	}

	private class InsertRecordTester extends RecordCrudTester {
		public void test0(int rounds) {
			RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
			for (int i = 0; i < rounds; i++) {
				String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE - 1) + " ";
				byte[] str = str0.getBytes();
				recordFile.insertRecord(str);
			}
			recordFile.close();
		}
	}

	private class GetRecordTester extends RecordCrudTester {
		public void test0(int rounds) {
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
	}

	private class UpdateRecordTester extends RecordCrudTester {
		public void test0(int rounds) {
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
				String newstr0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
				byte[] newstr = newstr0.getBytes();
				rf.updateRecord(rid, newstr);
				byte[] rec = rf.getRecord(rid);
				String rec0 = new String(rec, StandardCharsets.US_ASCII);
				assertEquals(newstr0, rec0);
			}
			rf.close();
		}
	}

	@Test
	public void testCrudRecord() {
		RecordCrudTester[] testers = new RecordCrudTester[] { new InsertRecordTester(), new GetRecordTester(),
				new UpdateRecordTester(), };
		int[] rounds_array = new int[] { 1, RandomUtils.nextInt(2, Page.DATA_SIZE / RECORD_SIZE),
				RandomUtils.nextInt(Page.DATA_SIZE / RECORD_SIZE + 1, Page.DATA_SIZE * 20 / RECORD_SIZE), };
		for (RecordCrudTester tester : testers) {
			for (int rounds : rounds_array) {
				tester.test(rounds);
			}
		}
	}

	@Test
	public void testDelete() {
		RecordFile rf = RecordFile.create(file, RECORD_SIZE);
		{
			String str0 = "abcdefgh";
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			RID rid = rf.insertRecord(str);
			rf.deleteRecord(rid);
		}
		{
			String str0 = "bcdefghi";
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			RID rid = rf.insertRecord(str);
			rf.deleteRecord(rid);
		}
		rf.close();
	}

	@Test
	public void testScan() {
		int rounds = 5;
		RecordFile rf = RecordFile.create(file, RECORD_SIZE);

		List<String> inserted = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			rf.insertRecord(str);
			inserted.add(str0);
		}

		Iterator<byte[]> iterator = rf.scan();
		Iterator<String> ii = inserted.iterator();
		while (iterator.hasNext()) {
			byte[] data = iterator.next();
			String str = new String(data, StandardCharsets.US_ASCII);
			assertTrue(ii.hasNext());
			assertEquals(ii.next(), str);
		}
	}

	@Test
	public void testScanWithPredicate() {
		int rounds = 15;
		RecordFile rf = RecordFile.create(file, RECORD_SIZE);

		List<String> inserted = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			rf.insertRecord(str);
			inserted.add(str0);
		}

		Predicate<byte[]> startsWith_A2N_a2n = (record) -> {
			byte b0 = record[0];
			return ((int) b0 >= (int) 'A' && (int) b0 <= (int) 'N') || ((int) b0 >= (int) 'a' && (int) b0 <= (int) 'n');
		};
		Iterator<byte[]> iterator = rf.scan(startsWith_A2N_a2n);
		Iterator<String> ii = inserted.iterator();
		while (ii.hasNext()) {
			String expected0 = ii.next();
			byte[] expected = expected0.getBytes(StandardCharsets.US_ASCII);
			if (!startsWith_A2N_a2n.test(expected)) {
				continue;
			}
			assert (iterator.hasNext());
			byte[] str = iterator.next();
			String str0 = new String(str, StandardCharsets.US_ASCII);
			assertEquals(expected0, str0);
		}
	}

}
