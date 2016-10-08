package me.nettee.pancake.core.page;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class PagedFileTest {
	
	private PagedFile pagedFile;
	private static File file = new File("/tmp/c.db");
	
	@BeforeClass
	public static void setUpBeforeClass() throws IOException {
		if (file.exists()) {
			file.delete();
		}
		file.createNewFile();
	}
	
	@Before
	public void setUp() {
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
	public void test1() throws IOException {
		pagedFile.allocatePage();
		
	}

}
