package me.nettee.pancake.core.record;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@Ignore
public class RecordFileCrudTest {

	private static final int RECORD_SIZE = 8;
	private static final int CAPACITY = 500;
	private RecordFile rf;
	private int rounds;

	@SuppressWarnings("rawtypes")
	@Parameters
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
	public void setUp() throws Exception {
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
			thrown.expect(RecordFileException.class);
			rf.getRecord(rid);
		}
	}
}
