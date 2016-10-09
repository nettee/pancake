package me.nettee.pancake.core.page;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class PagedFileTest {
	
	private PagedFile pagedFile;
	private File file;
	
	@BeforeClass
	public static void setUpBeforeClass() throws IOException {
		
	}
	
	@Before
	public void setUp() throws IOException {
		file = new File("/tmp/c.db");
		if (file.exists()) {
			file.delete();
		}
		file.createNewFile();
		
		try {
			pagedFile = PagedFile.open(file);
		} catch (FileNotFoundException e) {
			fail("");
		}
	}
	
	@After
	public void tearDown() throws IOException {
		pagedFile.close();
	}
	
	@Test
	@Ignore
	public void testAllocatePage() throws IOException {
		pagedFile.allocatePage();
	}
	
	@Test
	public void testAllocatePageTwice() throws IOException {
		pagedFile.allocatePage();
		pagedFile.allocatePage();
		pagedFile.allocatePage();
	}

}
