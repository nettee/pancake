package me.nettee.pancake.core.record;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecordFileScanTest {
	
	private static Logger logger = LoggerFactory.getLogger(RecordFileScanTest.class);

	private static final int RECORD_SIZE = 8;
	private static final int CAPACITY = 500;
	private RecordFile rf;
	private int rounds = 2;

	@Before
	public void setUp() throws Exception {
		File file = new File("/tmp/e.db");
		if (file.exists()) {
			file.delete();
		}
		rf = RecordFile.create(file, RECORD_SIZE);
	}

	@After
	public void tearDown() {
		rf.close();
	}

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void test() {
		String str1 = "abcdefg ";
		String str2 = "hijklmn ";
		rf.insertRecord(str1.getBytes(StandardCharsets.US_ASCII));
		rf.insertRecord(str2.getBytes(StandardCharsets.US_ASCII));
		Iterator<byte[]> recordIterator = rf.scan();
		while (recordIterator.hasNext()) {
			byte[] record = recordIterator.next();
			logger.debug("record: {}", new String(record, StandardCharsets.US_ASCII));
		}
	}

}
