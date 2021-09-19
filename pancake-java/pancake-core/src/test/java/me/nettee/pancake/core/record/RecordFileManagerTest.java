package me.nettee.pancake.core.record;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RecordFileManagerTest {

	private static final int RECORD_SIZE = 8;
	private Path path;

	@Before
	public void setUp() throws IOException {
		path = Paths.get("/tmp/c.db");
		Files.deleteIfExists(path);
	}
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	

	@Test
	public void testCreate() {
		RecordFile recordFile = RecordFile.create(path, RECORD_SIZE);
		recordFile.close();
	}

	@Test
	public void testCreate_createTwice() {
		RecordFile recordFile = RecordFile.create(path, RECORD_SIZE);
		recordFile.close();
		
		thrown.expect(Exception.class);
		RecordFile.create(path, RECORD_SIZE);
	}
	
	@Test
	public void testOpen() {
		RecordFile recordFile = RecordFile.create(path, RECORD_SIZE);
		recordFile.close();
		RecordFile recordFile2 = RecordFile.open(path);
		recordFile2.close();
	}
	
	@Test
	public void testOpen_withoutCreate() {
		thrown.expect(Exception.class);
		RecordFile.open(path);
	}
}
