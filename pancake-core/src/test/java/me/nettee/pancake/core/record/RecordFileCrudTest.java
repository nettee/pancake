package me.nettee.pancake.core.record;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static me.nettee.pancake.core.record.RecordFileTestUtils.getRandomRecord;
import static me.nettee.pancake.core.record.RecordFileTestUtils.insertRandomRecords;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class RecordFileCrudTest {

    private static Logger logger = LoggerFactory.getLogger(RecordFileCrudTest.class);

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

	private Record randomRecord() {
	    String str = RandomStringUtils.randomAlphabetic(RECORD_SIZE);
	    return Record.fromString(str);
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
		    Record newRecord = randomRecord();
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

    /**
     * TODO
     */
	@Test
    public void testReInsert() {
		List<Pair<Record, RID>> insertedRecords =
				insertRandomRecords(recordFile, rounds, RECORD_SIZE);
		List<RID> rids = insertedRecords.stream()
                .map(Pair::getRight)
                .collect(Collectors.toList());
        List<Integer> pageNums = rids.stream()
                .map(rid -> rid.pageNum)
                .distinct()
                .collect(Collectors.toList());
        System.out.printf("PageNums: %s\n", pageNums.stream()
                .map(pageNum -> String.valueOf(pageNum))
                .collect(Collectors.joining(", ", "[", "]")));

		// Randomly delete one record.
        int i = RandomUtils.nextInt(0, insertedRecords.size());
        Pair<Record, RID> pair = insertedRecords.get(i);
        RID rid = pair.getRight();
        recordFile.deleteRecord(rid);
        logger.debug("Record[{}] deleted", rid);
        System.out.printf("Record[%s] deleted\n", rid);
        Record newRecord = getRandomRecord(RECORD_SIZE);
        RID newRid = recordFile.insertRecord(newRecord);
        logger.debug("New RID: {}", newRid);
        System.out.printf("New RID: %s\n", newRid);
    }
}
