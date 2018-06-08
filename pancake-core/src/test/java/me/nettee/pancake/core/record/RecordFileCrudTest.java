package me.nettee.pancake.core.record;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class RecordFileCrudTest {

	private static final int RECORD_SIZE = 8;
	private static final int CAPACITY = 500;
	private RecordFile rf;
	private int rounds;

	@SuppressWarnings("rawtypes")
	@Parameters(name="{0} rounds")
	public static Collection data() {
		Object[][] data = {
				{1},
				{RandomUtils.nextInt(2, CAPACITY)},
				{CAPACITY},
				{CAPACITY + 1},
				{RandomUtils.nextInt(2, 10) * CAPACITY},
				{RandomUtils.nextInt(CAPACITY + 2, CAPACITY * 10)},
		};
		return Arrays.asList(data);
	}

	public RecordFileCrudTest(int rounds) {
		this.rounds = rounds;
	}
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Before
	public void setUp() {
		File file = new File("/tmp/c.db");
		if (file.exists()) {
			file.delete();
		}
		rf = RecordFile.create(file, RECORD_SIZE);
//		rf.setDebug(true);
	}

	@After
	public void tearDown() {
		rf.close();
	}

	@Test
	public void testInsert() {
		for (int i = 0; i < rounds; i++) {
			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE - 1) + " ";
			byte[] str = str0.getBytes();
			rf.insertRecord(str);
		}
	}

	@Test
	public void testGet() {
		List<Pair<RID, String>> records = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
			byte[] str = str0.getBytes();
			RID rid = rf.insertRecord(str);
			records.add(new ImmutablePair<>(rid, str0));
		}
		for (Pair<RID, String> record : records) {
			RID rid = record.getLeft();
			String str0 = record.getRight();
			byte[] rec = rf.getRecord(rid);
			String rec0 = new String(rec, StandardCharsets.US_ASCII);
			assertEquals(str0, rec0);
		}
	}
	
	@Test
	public void testUpdate() {
		List<RID> list = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
			byte[] str = str0.getBytes();
			RID rid = rf.insertRecord(str);
			list.add(rid);
		}
		Collections.shuffle(list);
		for (RID rid : list) {
			String newstr0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
			byte[] newstr = newstr0.getBytes();
			rf.updateRecord(rid, newstr);
			byte[] rec = rf.getRecord(rid);
			String rec0 = new String(rec, StandardCharsets.US_ASCII);
			assertEquals(newstr0, rec0);
		}
	}

	@Test
	public void testDelete() {
		List<RID> list = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
			String str0 = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
			byte[] str = str0.getBytes();
			RID rid = rf.insertRecord(str);
			list.add(rid);
		}
		Collections.shuffle(list);
		for (RID rid : list) {
			rf.deleteRecord(rid);
		}
		Collections.shuffle(list);
		for (RID rid : list) {
			thrown.expect(Exception.class);
			rf.getRecord(rid);
		}
	}
}
