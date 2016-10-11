package me.nettee.pancake.core.page;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class PagedFileTest {
	
	private File file1;
	private File file2;
	
	@BeforeClass
	public static void setUpBeforeClass() throws IOException {
		
	}
	
	@Before
	public void setUp() throws IOException {
		file1 = new File("/tmp/a.db");
		if (file1.exists()) {
			file1.delete();
		}
	}
	
	@After
	public void tearDown() throws IOException {
	}
	
	@Test
	public void testCreate() {
		PagedFile pagedFile = PagedFile.create(file1);
		pagedFile.close();
	}
	
	@Test
	@Ignore
	public void testAllocatePage() throws IOException {
	}
	
	@Test
	@Ignore
	public void testAllocatePageTwice() throws IOException {
	}

}
