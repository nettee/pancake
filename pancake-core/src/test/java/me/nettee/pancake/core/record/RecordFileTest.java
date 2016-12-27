package me.nettee.pancake.core.record;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Test;

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
	
	@Test
	public void testInsert0() {
		RecordFile rf = RecordFile.create(file, RECORD_SIZE);
		{
			String str0 = "abcdefg ";
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			RID rid = rf.insertRecord(str);
		}
		{
			String str0 = "bcdefgh ";
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			RID rid = rf.insertRecord(str);
		}
		rf.close();
	}
	
	@Test
	public void testGet0() {
		RecordFile rf = RecordFile.create(file, RECORD_SIZE);
		{
			String str0 = "abcdefg ";
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			RID rid = rf.insertRecord(str);
			byte[] gstr = rf.getRecord(rid);
			String gstr0 = new String(gstr, StandardCharsets.US_ASCII);
			assertEquals(str0, gstr0);
		}
		{
			String str0 = "bcdefgh ";
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			RID rid = rf.insertRecord(str);
			byte[] gstr = rf.getRecord(rid);
			String gstr0 = new String(gstr, StandardCharsets.US_ASCII);
			assertEquals(str0, gstr0);
		}
		rf.close();
	}
	
	@Test
	public void testUpdate0() {
		RecordFile rf = RecordFile.create(file, RECORD_SIZE);
		{
			String str0 = "abcdefg ";
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			RID rid = rf.insertRecord(str);
			String newstr0 = "opqrstu ";
			byte[] newstr = newstr0.getBytes(StandardCharsets.US_ASCII);
			rf.updateRecord(rid, newstr);
		}
		{
			String str0 = "bcdefgh ";
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			RID rid = rf.insertRecord(str);
		}
		rf.close();
	}
	
	@Test
	public void testDelete0() {
		RecordFile rf = RecordFile.create(file, RECORD_SIZE);
		RID rid0;
		{
			String str0 = "abcdefg ";
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			RID rid = rf.insertRecord(str);
			rid0 = rid;
		}
		{
			String str0 = "bcdefgh ";
			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
			RID rid = rf.insertRecord(str);
		}
		rf.deleteRecord(rid0);
		rf.close();
	}

//	@Test
//	public void testDelete() {
//		RecordFile rf = RecordFile.create(file, RECORD_SIZE);
//		{
//			String str0 = "abcdefgh";
//			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
//			RID rid = rf.insertRecord(str);
//			rf.deleteRecord(rid);
//		}
//		{
//			String str0 = "bcdefghi";
//			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
//			RID rid = rf.insertRecord(str);
//			rf.deleteRecord(rid);
//		}
//		rf.close();
//	}
//
//	@Test
//	public void testScan() {
//		int rounds = 5;
//		RecordFile rf = RecordFile.create(file, RECORD_SIZE);
//
//		List<String> inserted = new ArrayList<>();
//		for (int i = 0; i < rounds; i++) {
//			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
//			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
//			rf.insertRecord(str);
//			inserted.add(str0);
//		}
//
//		Iterator<byte[]> iterator = rf.scan();
//		Iterator<String> ii = inserted.iterator();
//		while (iterator.hasNext()) {
//			byte[] data = iterator.next();
//			String str = new String(data, StandardCharsets.US_ASCII);
//			assertTrue(ii.hasNext());
//			assertEquals(ii.next(), str);
//		}
//	}
//
//	@Test
//	public void testScanWithPredicate() {
//		int rounds = 15;
//		RecordFile rf = RecordFile.create(file, RECORD_SIZE);
//
//		List<String> inserted = new ArrayList<>();
//		for (int i = 0; i < rounds; i++) {
//			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
//			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
//			rf.insertRecord(str);
//			inserted.add(str0);
//		}
//
//		Predicate<byte[]> startsWith_A2N_a2n = (record) -> {
//			byte b0 = record[0];
//			return ((int) b0 >= (int) 'A' && (int) b0 <= (int) 'N') || ((int) b0 >= (int) 'a' && (int) b0 <= (int) 'n');
//		};
//		Iterator<byte[]> iterator = rf.scan(startsWith_A2N_a2n);
//		Iterator<String> ii = inserted.iterator();
//		while (ii.hasNext()) {
//			String expected0 = ii.next();
//			byte[] expected = expected0.getBytes(StandardCharsets.US_ASCII);
//			if (!startsWith_A2N_a2n.test(expected)) {
//				continue;
//			}
//			assert (iterator.hasNext());
//			byte[] str = iterator.next();
//			String str0 = new String(str, StandardCharsets.US_ASCII);
//			assertEquals(expected0, str0);
//		}
//	}
//	
//	@Test
//	public void test0() {
//		RecordFile rf = RecordFile.create(file, RECORD_SIZE);
//		{
//			String str0 = "abcdefg ";
//			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
//			RID rid = rf.insertRecord(str);
////			rf.deleteRecord(rid);
//		}
//		{
//			String str0 = "bcdefgh ";
//			byte[] str = str0.getBytes(StandardCharsets.US_ASCII);
//			RID rid = rf.insertRecord(str);
////			rf.deleteRecord(rid);
//		}
//		rf.ttt();
//		rf.close();
//	}

}
