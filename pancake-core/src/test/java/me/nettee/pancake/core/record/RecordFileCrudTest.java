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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class RecordFileCrudTest {

	private static final int RECORD_SIZE = 8;
	private static final int CAPACITY = 500;

	private RecordFile recordFile;
	private final int rounds;

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
		recordFile = RecordFile.create(file, RECORD_SIZE);
	}

	@After
	public void tearDown() {
		recordFile.close();
	}

	@Test
	public void testInsert() {
		for (int i = 0; i < rounds; i++) {
			String str = RandomStringUtils.randomAlphabetic(RECORD_SIZE - 1) + " ";
			recordFile.insertRecord(str.getBytes());
		}
	}

	@Test
	public void testGet() {
		List<Pair<RID, String>> records = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
			String str = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
			RID rid = recordFile.insertRecord(str.getBytes());
			records.add(new ImmutablePair<>(rid, str));
		}
		for (Pair<RID, String> record : records) {
			RID rid = record.getLeft();
			String str = record.getRight();
			byte[] data = recordFile.getRecord(rid);
			assertEquals(str, new String(data, StandardCharsets.US_ASCII));
		}
	}
	
	@Test
	public void testUpdate() {
		List<RID> rids = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
			String str = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
			RID rid = recordFile.insertRecord(str.getBytes());
			rids.add(rid);
		}
		Collections.shuffle(rids);
		for (RID rid : rids) {
			String newStr = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
			recordFile.updateRecord(rid, newStr.getBytes());
			byte[] data = recordFile.getRecord(rid);
			assertEquals(newStr, new String(data, StandardCharsets.US_ASCII));
		}
	}

	@Test
	public void testDelete() {
		List<RID> rids = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
			String str = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
			RID rid = recordFile.insertRecord(str.getBytes());
			rids.add(rid);
		}
		Collections.shuffle(rids);
		for (RID rid : rids) {
			recordFile.deleteRecord(rid);
		}
		Collections.shuffle(rids);
		for (RID rid : rids) {
			thrown.expect(RecordNotExistException.class);
			recordFile.getRecord(rid);
		}
	}
}
