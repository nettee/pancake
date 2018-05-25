package me.nettee.pancake.core.record;

import java.io.File;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@Ignore
public class RecordFileManagerTest {

	private static final int RECORD_SIZE = 8;
	private File file;

	@Before
	public void setUp() throws Exception {
		file = new File("/tmp/c.db");
		if (file.exists()) {
			file.delete();
		}
	}
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	

	@Test
	public void testCreate() {
		RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
		recordFile.close();
	}

	@Test
	public void testCreate_createTwice() {
		RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
		recordFile.close();
		
		thrown.expect(Exception.class);
		RecordFile.create(file, RECORD_SIZE);
	}
	
	@Test
	public void testOpen() {
		RecordFile recordFile = RecordFile.create(file, RECORD_SIZE);
		recordFile.close();
		RecordFile recordFile2 = RecordFile.open(file);
		recordFile2.close();
	}
	
	@Test
	public void testOpen_withoutCreate() {
		thrown.expect(Exception.class);
		RecordFile.open(file);
	}
}
