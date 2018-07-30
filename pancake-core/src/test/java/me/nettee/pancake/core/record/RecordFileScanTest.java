package me.nettee.pancake.core.record;

import java.io.File;
import java.util.*;

import me.nettee.pancake.core.page.Pages;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class RecordFileScanTest {
	
	private static Logger logger = LoggerFactory.getLogger(RecordFileScanTest.class);

	private static final int RECORD_SIZE = 8;
	private static final int CAPACITY = 500;

	private RecordFile recordFile;
	private final int rounds;

	@SuppressWarnings("rawtypes")
	@Parameterized.Parameters(name="{0} rounds")
	public static Collection data() {
		Object[][] data = {
//				{1},
//				{RandomUtils.nextInt(2, CAPACITY)},
//				{CAPACITY},
				{CAPACITY + 1},
                {2 * CAPACITY + 5},
//				{RandomUtils.nextInt(2, 10) * CAPACITY},
//				{RandomUtils.nextInt(CAPACITY + 2, CAPACITY * 10)},
		};
		return Arrays.asList(data);
	}

	public RecordFileScanTest(int rounds) {
		this.rounds = rounds;
	}

	@Before
	public void setUp() throws Exception {
		File file = new File("/tmp/e.db");
		if (file.exists()) {
			file.delete();
		}
		recordFile = RecordFile.create(file, RECORD_SIZE);
	}

	@After
	public void tearDown() {
		recordFile.close();
	}

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void testScan() {
		List<RID> rids = new ArrayList<>();
		List<byte[]> records = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
			String str = String.format("rec-%04d", i);
			byte[] record = str.getBytes();
			records.add(record);
			RID rid = recordFile.insertRecord(record);
			rids.add(rid);
		}
//		Map<Integer, Set<Integer>> counter = new TreeMap<>();
//		for (RID rid : rids) {
//			if (!counter.containsKey(rid.pageNum)) {
//				counter.put(rid.pageNum, new HashSet<>());
//			}
//			counter.get(rid.pageNum).add(rid.slotNum);
//		}
//		for (Map.Entry<Integer, Set<Integer>> entry : counter.entrySet()) {
//			int pageNum = entry.getKey();
//			Set<Integer> slots = entry.getValue();
//			logger.debug("Page[{}] contains records [{}]",
//					pageNum, Pages.pageRangeRepr(slots));
//		}

		int i = 0;
		Iterator<byte[]> iterator = recordFile.scan();
		while (iterator.hasNext()) {
			byte[] record = iterator.next();
			byte[] expected = records.get(i);
			i++;
            assertTrue(Records.equals(expected, record));
		}
	}
}
