package me.nettee.pancake.core.page;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PagedFileManagerTest {
	
	private Path path;
	
	@BeforeClass
	public static void setUpBeforeClass() {
		
	}
	
	@Before
	public void setUp() throws IOException {
		path = Paths.get("/tmp/b.db");
		Files.deleteIfExists(path);
	}
	
	@After
	public void tearDown() {
	}
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	/**
	 * A paged file can be created.
	 */
	@Test
	public void testCreate() {
		PagedFile pagedFile = PagedFile.create(path);
		pagedFile.close();
	}

	/**
	 * A paged file cannot be created more than once.
	 */
	@Test
	public void testCreate_createTwice() {
		PagedFile pf = PagedFile.create(path);
		pf.close();
		thrown.expect(Exception.class);
		PagedFile.create(path);
	}

	/**
	 * A paged file can be opened when it is already created.
	 */
	@Test
	public void testOpen() {
		PagedFile pagedFile = PagedFile.create(path);
		pagedFile.close();
		PagedFile pagedFile2 = PagedFile.open(path);
		pagedFile2.close();
	}

	/**
	 * A paged file cannot be opened without being created.
	 */
	@Test
	public void testOpen_withoutCreate() {
		thrown.expect(Exception.class);
		PagedFile.open(path);
	}
	
}
