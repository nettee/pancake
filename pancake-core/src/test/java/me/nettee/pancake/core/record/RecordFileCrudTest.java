package me.nettee.pancake.core.record;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordFileCrudTest {

	private static final int RECORD_SIZE = 8;
	private RecordFile rf;
	private int rounds;

	@SuppressWarnings("rawtypes")
	@Parameters
	public static Collection data() {
		Object[][] data = {
				{1},
				{10},
		};
		return Arrays.asList(data);
	}

	public RecordFileCrudTest(int rounds) {
		this.rounds = rounds;
	}

	@Before
	public void setUp() throws Exception {
		File file = new File("/tmp/c.db");
		if (file.exists()) {
			file.delete();
		}
		rf = RecordFile.create(file, RECORD_SIZE);
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
}
