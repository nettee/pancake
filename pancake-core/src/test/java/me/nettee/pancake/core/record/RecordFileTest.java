package me.nettee.pancake.core.record;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import me.nettee.pancake.core.page.PagedFile;

public class RecordFileTest {
	
	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testRecordFile() throws IOException {
		File file = new File("/tmp/d.db");
		if (file.exists()) {
			file.delete();
		}
		file.createNewFile();
		PagedFile pagedFile = PagedFile.open(file);
		new RecordFile(pagedFile, 8);
	}

}
