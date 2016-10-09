package me.nettee.pancake.core.record;

import java.io.File;
import java.io.FileNotFoundException;

import org.junit.Before;
import org.junit.Test;

import me.nettee.pancake.core.page.PagedFile;

public class RecordFileTest {
	
	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testRecordFile() throws FileNotFoundException {
		File file = new File("/tmp/d.db");
		PagedFile pagedFile = PagedFile.open(file);
		new RecordFile(pagedFile, 8);
	}

}
