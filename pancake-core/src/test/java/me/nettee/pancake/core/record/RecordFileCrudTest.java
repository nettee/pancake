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
import static org.junit.Assert.assertTrue;
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
	    List<RID> rids = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
            Record record = randomRecord();
            RID rid = recordFile.insertRecord(record);
            rids.add(rid);
        }
        Set<RID> ridSet = new HashSet<>(rids);
		assertEquals(rids.size(), ridSet.size());
	}

    /**
     * The inserted records can be retrieved by their RIDs.
     */
	@Test
	public void testGet() {
		List<Pair<RID, Record>> insertedRecords = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
            Record record = randomRecord();
			RID rid = recordFile.insertRecord(record);
			insertedRecords.add(new ImmutablePair<>(rid, record));
		}
		for (Pair<RID, Record> pair : insertedRecords) {
			RID rid = pair.getLeft();
			Record expectedRecord = pair.getRight();
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
		List<RID> rids = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
		    Record record = randomRecord();
			RID rid = recordFile.insertRecord(record);
			rids.add(rid);
		}
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
		List<RID> rids = new ArrayList<>();
		for (int i = 0; i < rounds; i++) {
		    Record record = randomRecord();
			RID rid = recordFile.insertRecord(record);
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

	@Test
    public void testReInsert() {

    }
}
