package me.nettee.pancake.core.page;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PagedFileTest {
	
	private File file;
	
	@BeforeClass
	public static void setUpBeforeClass() throws IOException {
		
	}
	
	@Before
	public void setUp() throws IOException {
		file = new File("/tmp/a.db");
		if (file.exists()) {
			file.delete();
		}
	}
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	@After
	public void tearDown() throws IOException {
	}
	
	@Test
	public void testCreate() {
		PagedFile pagedFile = PagedFile.create(file);
		pagedFile.close();
	}
	
	@Test
	public void testOpen() throws IOException {
		PagedFile pagedFile = PagedFile.create(file);
		pagedFile.close();
		
		pagedFile = PagedFile.open(file);
		pagedFile.close();
	}
	
	@Test
	public void testCreateCreate() {
		PagedFile pf = PagedFile.create(file);
		pf.close();
		
		thrown.expect(PagedFileException.class);
		PagedFile.create(file);
	}
	
	@Test
	public void testOpenWithoutCreate() throws IOException {
		thrown.expect(PagedFileException.class);
		PagedFile.open(file);
	}
	
	@Test
	public void testAllocatePage() throws IOException {
		PagedFile pagedFile = PagedFile.create(file);
		pagedFile.allocatePage();
		pagedFile.close();
	}
	
	@Test
	public void testAllocatePage2() throws IOException {
		PagedFile pagedFile = PagedFile.create(file);
		for (int i = 0; i < 50; i++) {
			pagedFile.allocatePage();
		}
		pagedFile.close();
	}
	
	@Test
	public void testGetNumOfPages() throws IOException {
		PagedFile pagedFile = PagedFile.create(file);
		for (int i = 0; i < 50; i++) {
			pagedFile.allocatePage();
			assertEquals(i+1, pagedFile.getNumOfPages());
		}
		pagedFile.close();
	}
}
