package me.nettee.pancake.core.record;

import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.model.Record;
import me.nettee.pancake.core.model.Scan;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static me.nettee.pancake.core.record.RecordFileTestUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class RecordFileScanTest {
	
	private static final int RECORD_SIZE = 8;

	private RecordFile recordFile;
	private final int rounds;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

	@SuppressWarnings("rawtypes")
	@Parameterized.Parameters(name="{0} rounds")
	public static Collection data() {
		return randomRecordNumbers(RECORD_SIZE);
	}

	public RecordFileScanTest(int rounds) {
		this.rounds = rounds;
	}

	@Before
	public void setUp() throws IOException {
        Path path = Paths.get("/tmp/e.db");
        Files.deleteIfExists(path);
		recordFile = RecordFile.create(path, RECORD_SIZE);
	}

	@After
	public void tearDown() {
		recordFile.close();
	}

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

    private void checkScanAll(List<Record> expectedRecords, Scan<Record> scan) {
        int i = 0;
        while (true) {
            Optional<Record> optionalRecord = scan.next();
            if (!optionalRecord.isPresent()) {
                // End of scan.
                break;
            }
            Record record = optionalRecord.get();
            Record expected = expectedRecords.get(i);
            assertTrue(i < expectedRecords.size());
            assertEquals(expected, record);
            i++;
        }
        assertEquals(expectedRecords.size(), i);
    }

    private void checkScanNone(Scan<Record> scan) {
	    checkScanAll(Collections.emptyList(), scan);
    }

    private void checkScanPartial(List<Record> allRecords,
                                  Predicate<Record> predicate,
                                  Scan<Record> scan) {
        List<Record> targetRecords = allRecords.stream()
                .filter(predicate)
                .collect(Collectors.toList());
        checkScanAll(targetRecords, scan);
    }

    /**
     * We will get all records with {@code scan()}.
     */
	@Test
    public void testScan() {
        List<Record> records = insertSequenceRecords2(recordFile);
        checkScanAll(records, recordFile.scan());
    }

    /**
     * We will get all records with {@code scan()}, except the deleted one.
     */
    @Test
    public void testScan_deleteOne() {
        List<Pair<Record, RID>> pairs = insertSequenceRecords(recordFile);
        List<Record> records = pairs.stream()
                .map(Pair::getLeft)
                .collect(Collectors.toList());
        Pair<Record, RID> recordToDelete = pickOne(pairs);
        records.remove(recordToDelete.getLeft());
        recordFile.deleteRecord(recordToDelete.getRight());
        checkScanAll(records, recordFile.scan());
    }

    /**
     * We will get all records with {@code scan()}, except deleted ones.
     */
    @Test
    public void testScan_deleteSome() {
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
        checkScanAll(records, recordFile.scan());

    }

    /**
     * We will get all records using {@code scan()} with {@code true} predicate.
     */
    @Test
    public void testScanPredicate_truePredicate() {
        List<Record> records = insertSequenceRecords2(recordFile);
        Predicate<Record> predicate = r -> true;
        checkScanAll(records, recordFile.scan(predicate));
    }

    /**
     * We will get no records using {@code scan()} with {@code false} predicate.
     */
    @Test
    public void testScanPredicate_falsePredicate() {
	    insertSequenceRecords2(recordFile);
        Predicate<Record> predicate = r -> false;
        checkScanNone(recordFile.scan(predicate));
    }

    /**
     * We will get all records using {@code scan()} with a predicate that is
     * always true.
     */
    @Test
    public void testScanPredicate_allTruePredicate() {
	    List<Record> records = insertSequenceRecords2(recordFile);
        Predicate<Record> predicate = r -> r.toString().startsWith("rec");
        checkScanAll(records, recordFile.scan(predicate));
    }

    /**
     * We will get no records using {@code scan()} with a predicate that is
     * always false.
     */
    @Test
    public void testScanPredicate_allFalsePredicate() {
	    insertSequenceRecords2(recordFile);
        Predicate<Record> predicate = r -> r.toString().startsWith("cer");
        checkScanNone(recordFile.scan(predicate));
    }

    /**
     * When {@code scan()} the record file with a predicate, we will get records
     * that satisfies the predicate.
     */
    @Test
    public void testScanPredicate() {
        List<Record> records = insertSequenceRecords2(recordFile);
        checkScanPartial(records, evenPredicate, recordFile.scan(evenPredicate));
    }

    /**
     * If we delete all non-even records, and then scan even records, we will
     * get all even records.
     */
    @Test
    public void testScanPredicate_nonCandidateDeleted() {
        List<Pair<Record, RID>> pairs = insertSequenceRecords(recordFile);
        List<Record> records = pairs.stream()
                .map(Pair::getLeft)
                .collect(Collectors.toList());
        pairs.stream()
                .filter(pair -> evenPredicate.negate().test(pair.getLeft()))
                .forEach(pair -> recordFile.deleteRecord(pair.getRight()));
        checkScanPartial(records, evenPredicate, recordFile.scan(evenPredicate));
    }

    /**
     * If we delete all even records, and then scan even records, we will get
     * no records.
     */
    @Test
    public void testScanPredicate_candidateDeleted() {
        List<Pair<Record, RID>> pairs = insertSequenceRecords(recordFile);
        pairs.stream()
                .filter(pair -> evenPredicate.test(pair.getLeft()))
                .forEach(pair -> recordFile.deleteRecord(pair.getRight()));
        checkScanNone(recordFile.scan(evenPredicate));
    }

}
