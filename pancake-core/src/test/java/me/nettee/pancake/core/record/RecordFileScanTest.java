package me.nettee.pancake.core.record;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static me.nettee.pancake.core.record.RecordFileTestUtils.insertRecords;
import static me.nettee.pancake.core.record.RecordFileTestUtils.pickOne;
import static me.nettee.pancake.core.record.RecordFileTestUtils.pickSome;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class RecordFileScanTest {
	
	private static final int RECORD_SIZE = 8;
	private static final int CAPACITY = 500;

	private RecordFile recordFile;
	private final int rounds;

	@SuppressWarnings("rawtypes")
	@Parameterized.Parameters(name="{0} rounds")
	public static Collection data() {
		Object[][] data = {
				{1},
				{RandomUtils.nextInt(2, CAPACITY)},
				{CAPACITY},
				{CAPACITY + 1},
                {2 * CAPACITY + 5},
				{RandomUtils.nextInt(2, 10) * CAPACITY},
				{RandomUtils.nextInt(CAPACITY + 2, CAPACITY * 10)},
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

    private Predicate<Record> evenPredicate = r -> {
        String s = r.toString();
        int i = Integer.valueOf(s.substring(4));
        return i % 2 == 0;
    };

	private List<Record> insertSequenceRecords2(RecordFile recordFile) {
        List<Pair<Record, RID>> insertedRecords = insertSequenceRecords(recordFile);
        return insertedRecords.stream()
                .map(Pair::getLeft)
                .collect(Collectors.toList());
    }

    private List<Pair<Record, RID>> insertSequenceRecords(RecordFile recordFile) {
        IntFunction<Record> recordGenerator = i -> {
            String str = String.format("rec-%04d", i);
            return Record.fromString(str);
        };
        return insertRecords(recordFile, rounds, recordGenerator);
    }

    private void testScanAll(List<Record> expectedRecords, Scan<Record> scan) {
        int i = 0;
        while (true) {
            Optional<Record> optionalRecord = scan.next();
            if (!optionalRecord.isPresent()) {
                // End of scan.
                break;
            }
            Record record = optionalRecord.get();
            Record expected = expectedRecords.get(i);
            assertEquals(expected, record);
            i++;
        }
        assertEquals(expectedRecords.size(), i);
    }

    private void testScanNone(Scan<Record> scan) {
	    int c = 0;
	    while (true) {
            Optional<Record> optionalRecord = scan.next();
            if (!optionalRecord.isPresent()) {
                // End of scan.
                break;
            }
            c++;
        }
        assertEquals(0, c);
    }

    private void testScanPartial(List<Record> allRecords,
                                 Predicate<Record> predicate,
                                 Scan<Record> scan) {
        List<Record> targetRecords = allRecords.stream()
                .filter(predicate)
                .collect(Collectors.toList());
        testScanAll(targetRecords, scan);
    }

	@Test
    public void testScan_noPredicate() {
        List<Record> records = insertSequenceRecords2(recordFile);
        testScanAll(records, recordFile.scan());
    }

    @Test
    public void testScan_noPredicate_withDeleteOne() {
        List<Pair<Record, RID>> pairs = insertSequenceRecords(recordFile);
        List<Record> records = pairs.stream()
                .map(Pair::getLeft)
                .collect(Collectors.toList());
        Pair<Record, RID> recordToDelete = pickOne(pairs);
        records.remove(recordToDelete.getLeft());
        recordFile.deleteRecord(recordToDelete.getRight());
        testScanAll(records, recordFile.scan());
    }

    @Test
    public void testScan_noPredicate_withDeleteSome() {
        List<Pair<Record, RID>> pairs = insertSequenceRecords(recordFile);
        List<Record> records = pairs.stream()
                .map(Pair::getLeft)
                .collect(Collectors.toList());
        int m = rounds / 11 + 1;
        List<Pair<Record, RID>> pairsToDelete = pickSome(pairs, m);
        List<Record> recordsToDelete = pairsToDelete.stream()
                .map(Pair::getLeft)
                .collect(Collectors.toList());
        List<RID> ridsToDelete = pairsToDelete.stream()
                .map(Pair::getRight)
                .collect(Collectors.toList());
        records.removeAll(recordsToDelete);
        for (RID rid : ridsToDelete) {
            recordFile.deleteRecord(rid);
        }
        testScanAll(records, recordFile.scan());

    }

    @Test
    public void testScan_truePredicate() {
        List<Record> records = insertSequenceRecords2(recordFile);
        Predicate<Record> predicate = r -> true;
        testScanAll(records, recordFile.scan(predicate));
    }

    @Test
    public void testScan_falsePredicate() {
	    insertSequenceRecords2(recordFile);
        Predicate<Record> predicate = r -> false;
        testScanNone(recordFile.scan(predicate));
    }

    @Test
    public void testScan_allTruePredicate() {
	    List<Record> records = insertSequenceRecords2(recordFile);
        Predicate<Record> predicate = r -> r.toString().startsWith("rec");
        testScanAll(records, recordFile.scan(predicate));
    }

    @Test
    public void testScan_allFalsePredicate() {
	    insertSequenceRecords2(recordFile);
        Predicate<Record> predicate = r -> r.toString().startsWith("cer");
        testScanNone(recordFile.scan(predicate));
    }

    @Test
    public void testScan_partialTruePredicate() {
        List<Record> records = insertSequenceRecords2(recordFile);
        testScanPartial(records, evenPredicate, recordFile.scan(evenPredicate));
    }

    /**
     * If we delete all non-even records, and then scan even records, we will
     * get all even records.
     */
    @Test
    public void testScan_nonCandidateDeleted() {
        List<Pair<Record, RID>> pairs = insertSequenceRecords(recordFile);
        List<Record> records = pairs.stream()
                .map(Pair::getLeft)
                .collect(Collectors.toList());
        pairs.stream()
                .filter(pair -> evenPredicate.negate().test(pair.getLeft()))
                .forEach(pair -> recordFile.deleteRecord(pair.getRight()));
        testScanPartial(records, evenPredicate, recordFile.scan(evenPredicate));
    }

    /**
     * If we delete all even records, and then scan even records, we will get
     * no records.
     */
    @Test
    public void testScan_candidateDeleted() {
        List<Pair<Record, RID>> pairs = insertSequenceRecords(recordFile);
        pairs.stream()
                .filter(pair -> evenPredicate.test(pair.getLeft()))
                .forEach(pair -> recordFile.deleteRecord(pair.getRight()));
        testScanNone(recordFile.scan(evenPredicate));
    }

}
