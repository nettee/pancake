package me.nettee.pancake.core.page;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;

public class PagedFileManagerTest {
	
	private File file;
	
	@BeforeClass
	public static void setUpBeforeClass() throws IOException {
		
	}
	
	@Before
	public void setUp() throws IOException {
		file = new File("/tmp/b.db");
		if (file.exists()) {
			file.delete();
		}
	}
	
	@After
	public void tearDown() throws IOException {
	}
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	/**
	 * A paged file can be created.
	 */
	@Test
	public void testCreate() {
		PagedFile pagedFile = PagedFile.create(file);
		pagedFile.close();
	}

	/**
	 * A paged file cannot be created more than once.
	 */
	@Test
	public void testCreate_createTwice() {
		PagedFile pf = PagedFile.create(file);
		pf.close();
		thrown.expect(Exception.class);
		PagedFile.create(file);
	}

	/**
	 * A paged file can be opened when it is already created.
	 */
	@Test
	public void testOpen() {
		PagedFile pagedFile = PagedFile.create(file);
		pagedFile.close();
		PagedFile pagedFile2 = PagedFile.open(file);
		pagedFile2.close();
	}

	/**
	 * A paged file cannot be opened without being created.
	 */
	@Test
	public void testOpen_withoutCreate() {
		thrown.expect(Exception.class);
		PagedFile.open(file);
	}
	
}
