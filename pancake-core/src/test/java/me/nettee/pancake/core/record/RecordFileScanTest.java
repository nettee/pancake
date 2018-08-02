package me.nettee.pancake.core.record;

import me.nettee.pancake.core.page.Pages;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
//                {2 * CAPACITY + 5},
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

	private List<byte[]> insertRecords(RecordFile recordFile) {
        List<RID> rids = new ArrayList<>();
        List<byte[]> records = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            String str = String.format("rec-%04d", i);
            byte[] record = str.getBytes();
            records.add(record);
            RID rid = recordFile.insertRecord(record);
            rids.add(rid);
        }

        Map<Integer, Set<Integer>> counter = new TreeMap<>();
        for (RID rid : rids) {
            if (!counter.containsKey(rid.pageNum)) {
                counter.put(rid.pageNum, new HashSet<>());
            }
            counter.get(rid.pageNum).add(rid.slotNum);
        }
        for (Map.Entry<Integer, Set<Integer>> entry : counter.entrySet()) {
            int pageNum = entry.getKey();
            Set<Integer> slots = entry.getValue();
            logger.debug("Page[{}] contains records [{}]",
                    pageNum, Pages.pageRangeRepr(slots));
        }

        return records;
    }

    private void debugScan(Scan<byte[]> scan) {
	    while (true) {
	        Optional<byte[]> optionalRecord = scan.next();
	        if (!optionalRecord.isPresent()) {
	            // End of scan.
                break;
            }
            byte[] record = optionalRecord.get();
	        String str = new String(record);
	        System.out.println(str);
        }
    }

    private void testScanAll(List<byte[]> expectedRecords, Scan<byte[]> scan) {
        int i = 0;
        while (true) {
            Optional<byte[]> optionalRecord = scan.next();
            if (!optionalRecord.isPresent()) {
                // End of scan.
                break;
            }
            byte[] record = optionalRecord.get();
            byte[] expected = expectedRecords.get(i);
            assertTrue(Records.equals(expected, record));
            i++;
        }
        assertEquals(expectedRecords.size(), i);
    }

    private void testScanNone(Scan<byte[]> scan) {
	    int c = 0;
	    while (true) {
            Optional<byte[]> optionalRecord = scan.next();
            if (!optionalRecord.isPresent()) {
                // End of scan.
                break;
            }
            c++;
        }
        assertEquals(0, c);
    }

    private void testScanPartial(List<byte[]> allRecords,
                                 Predicate<byte[]> predicate,
                                 Scan<byte[]> scan) {
        List<byte[]> targetRecords = allRecords.stream()
                .filter(predicate)
                .collect(Collectors.toList());
        testScanAll(targetRecords, scan);
    }

	@Test
    public void testScan_noPredicate() {
        List<byte[]> records = insertRecords(recordFile);
        testScanAll(records, recordFile.scan());
    }

    @Test
    public void testScan_truePredicate() {
        List<byte[]> records = insertRecords(recordFile);
        Predicate<byte[]> predicate = r -> true;
        testScanAll(records, recordFile.scan(predicate));
    }

    @Test
    public void testScan_falsePredicate() {
	    insertRecords(recordFile);
        Predicate<byte[]> predicate = r -> false;
        testScanNone(recordFile.scan(predicate));
    }

    @Test
    public void testScan_allTruePredicate() {
	    List<byte[]> records = insertRecords(recordFile);
        Predicate<byte[]> predicate = r -> new String(r).startsWith("rec");
        testScanAll(records, recordFile.scan(predicate));
    }

    @Test
    public void testScan_allFalsePredicate() {
	    insertRecords(recordFile);
        Predicate<byte[]> predicate = r -> new String(r).startsWith("cer");
        testScanNone(recordFile.scan(predicate));
    }

    @Test
    public void testScan_partialTruePredicate() {
        List<byte[]> records = insertRecords(recordFile);
        Predicate<byte[]> predicate = r -> {
            String s = new String(r);
            int i = Integer.valueOf(s.substring(4));
            return i % 2 == 0;
        };
        testScanPartial(records, predicate, recordFile.scan(predicate));
    }

    @Test
    public void testScanWithDeletedRecords() {
	    insertRecords(recordFile);
	    debugScan(recordFile.scan());
    }
}
