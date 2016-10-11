package me.nettee.pancake.core.record;

import java.io.File;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import me.nettee.pancake.core.page.PagedFile;

public class RecordFileTest {
	
	private RecordFile recordFile;

	@Before
	public void setUp() throws Exception {
		File file = new File("/tmp/d.db");
		if (file.exists()) {
			file.delete();
		}
		file.createNewFile();
		PagedFile pagedFile = PagedFile.open(file);
		recordFile = RecordFile.create(pagedFile, 8);
	}

	@Test
	@Ignore
	public void testInsertRecord() {
		byte[] str = "abcdefgh".getBytes();
		recordFile.insertRecord(str);
	}

}
