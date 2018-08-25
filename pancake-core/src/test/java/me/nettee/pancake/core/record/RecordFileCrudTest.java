package me.nettee.pancake.core.record;

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
import java.util.*;
import java.util.stream.Collectors;

import static me.nettee.pancake.core.record.RecordFileTestUtils.*;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class RecordFileCrudTest {

	private static final int RECORD_SIZE = 8;

	private RecordFile recordFile;
	private final int rounds;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@SuppressWarnings("rawtypes")
	@Parameters(name="{0} rounds")
	public static Collection data() {
		return randomRecordNumbers(RECORD_SIZE);
	}

	public RecordFileCrudTest(int rounds) {
		this.rounds = rounds;
	}

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

	/**
     * Records can be inserted into a record file. The RIDs of each record
     * must be unique.
     */
	@Test
	public void testInsert() {
        List<Pair<Record, RID>> insertedRecords =
                insertRandomRecords(recordFile, rounds, RECORD_SIZE);
        List<RID> rids = insertedRecords.stream()
                .map(Pair::getRight)
                .collect(Collectors.toList());
        Set<RID> ridSet = new HashSet<>(rids);
		assertEquals(rids.size(), ridSet.size());
	}

    /**
     * The inserted records can be retrieved by their RIDs.
     */
	@Test
	public void testGet() {
		List<Pair<Record, RID>> insertedRecords =
                insertRandomRecords(recordFile, rounds, RECORD_SIZE);
        for (Pair<Record, RID> pair : insertedRecords) {
            RID rid = pair.getRight();
			Record expectedRecord = pair.getLeft();
			Record actualRecord = recordFile.getRecord(rid);
			assertEquals(expectedRecord, actualRecord);
		}
	}

    /**
     * If we update a record and retrieve its content, we will get the updated
     * content.
     */
	@Test
	public void testUpdate() {
        List<Pair<Record, RID>> insertedRecords =
                insertRandomRecords(recordFile, rounds, RECORD_SIZE);
        List<RID> rids = insertedRecords.stream()
                .map(Pair::getRight)
                .collect(Collectors.toList());
        Collections.shuffle(rids);
		for (RID rid : rids) {
			Record newRecord = getRandomRecord(RECORD_SIZE);
			recordFile.updateRecord(rid, newRecord);
			Record actualRecord = recordFile.getRecord(rid);
			assertEquals(newRecord, actualRecord);
		}
	}

    /**
     * If we retrieve a deleted record (via its RID), an exception will be
     * thrown.
     */
	@Test
	public void testDelete() {
        List<Pair<Record, RID>> insertedRecords =
                insertRandomRecords(recordFile, rounds, RECORD_SIZE);
        List<RID> rids = insertedRecords.stream()
                .map(Pair::getRight)
                .collect(Collectors.toList());
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

	@Test
    public void testReInsert() {
		List<Pair<Record, RID>> insertedRecords =
				insertRandomRecords(recordFile, rounds, RECORD_SIZE);

		// Randomly delete one record.
        RID oldRid = pickOne(insertedRecords).getRight();
        recordFile.deleteRecord(oldRid);

        // Insert a random record, and its RID should be the same as the old
        // one.
        Record newRecord = getRandomRecord(RECORD_SIZE);
        RID newRid = recordFile.insertRecord(newRecord);

        assertEquals(oldRid, newRid);
    }

    // TODO Hidden bug here. Test fails in some executions.
    @Test
    public void testReInsert2() {
        List<Pair<Record, RID>> insertedRecords =
                insertRandomRecords(recordFile, rounds, RECORD_SIZE);

        // Randomly delete some records.
        int m = insertedRecords.size() / 11 + 1;
        List<RID> oldRids = pickSome(insertedRecords, m).stream()
                .map(Pair::getRight)
                .collect(Collectors.toList());
        for (RID rid : oldRids) {
            recordFile.deleteRecord(rid);
        }

        // Insert a same number of random records, and their RIDs should be the
        // same as the old ones, ignoring ordering.
        List<RID> newRids = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            RID newRid = recordFile.insertRecord(getRandomRecord(RECORD_SIZE));
            newRids.add(newRid);
        }

        Collections.sort(oldRids);
        Collections.sort(newRids);
        assertEquals(oldRids, newRids);
    }
}

