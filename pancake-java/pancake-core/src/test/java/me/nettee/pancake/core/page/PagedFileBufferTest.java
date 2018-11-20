package me.nettee.pancake.core.page;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static me.nettee.pancake.core.page.PagedFileTestUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PagedFileBufferTest {

	private static final Path path = Paths.get("/tmp/c.db");
	private PagedFile pagedFile;

	@BeforeClass
	public static void setUpBeforeClass() {
	}

	@Before
	public void setUp() throws IOException {
		Files.deleteIfExists(path);
		pagedFile = PagedFile.create(path);
	}

	@After
	public void tearDown() {
		pagedFile.close();
	}

	private void reOpen() {
		pagedFile.close();
		pagedFile = PagedFile.open(path);
	}

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	/**
	 * A page can be marked as dirty.
	 */
	@Test
	public void testMarkDirty() {
		int N = allocatePages(pagedFile);
		for (int i = 0; i < N; i++) {
			pagedFile.markDirty(i);
		}
		unpinPages(pagedFile, N);
	}

	/**
	 * A page to be marked as dirty must be in the buffer.
	 */
	@Test
	public void testMarkDirty_notInBuffer() {
		int N = allocatePages(pagedFile);
		unpinPages(pagedFile, N);
		reOpen();
		for (int i = 0; i < N; i++) {
			thrown.expect(PagedFileException.class);
			pagedFile.markDirty(i);
		}
	}

	/**
	 * A page to be marked as dirty must be pinned in the buffer.
	 */
	@Test
	public void testMarkDirty_notPinned() {
		int N = allocatePages(pagedFile);
		unpinPages(pagedFile, N);
		for (int i = 0; i < N; i++) {
			thrown.expect(PagedFileException.class);
			pagedFile.markDirty(i);
		}
	}

	/**
	 * When the buffer is full, no pages can be pinned to the buffer.
	 */
	@Test
	public void testBufferFull() {
		// Make buffer full
		for (int i = 0; i < PageBuffer.BUFFER_SIZE; i++) {
			pagedFile.allocatePage();
		}
		try {
			pagedFile.allocatePage();
			fail("expect FullBufferException to throw");
		} catch (FullBufferException e) {
			// expected
		}
		unpinPages(pagedFile, PageBuffer.BUFFER_SIZE);
	}

	/**
	 * When the buffer is full, unpinned pages will be removed to save space
	 * for newly pinned pages
	 */
	@Test
	public void testBufferFull_removeUnpinned() {
		// Make buffer full
		for (int i = 0; i < PageBuffer.BUFFER_SIZE; i++) {
			pagedFile.allocatePage();
		}
		pagedFile.unpinPage(0);
		// No exception here
		Page page1 = pagedFile.allocatePage();

		for (int i = 1; i < PageBuffer.BUFFER_SIZE; i++) {
			pagedFile.unpinPage(i);
		}
		pagedFile.unpinPage(page1);
	}

	/**
	 * The buffer will not be full if pages are unpinned in time.
	 */
	@Test
	public void testBufferNeverFull() {
		// Allocate pages more than buffer size.
		for (int i = 0; i < 3 * PageBuffer.BUFFER_SIZE; i++) {
			Page page = pagedFile.allocatePage();
			pagedFile.unpinPage(page.num);
		}
	}

	/**
	 * An page in buffer (just allocated) can be got by <tt>getPage</tt>,
	 * without pinning it twice.
	 */
	@Test
	public void testPinUnpin_allocateAndGet() {
		Page page = pagedFile.allocatePage();
		pagedFile.getPage(page.num);
		pagedFile.unpinPage(page);
	}

	/**
	 * A page in buffer can be got another time by <tt>getPage</tt>, without
	 * pinning it twice.
	 */
	@Test
	public void testPinUnpin_getTwice() {
		Page page = pagedFile.allocatePage();
		pagedFile.unpinPage(page);
		reOpen();
		pagedFile.getPage(0);
		pagedFile.getPage(0);
		pagedFile.unpinPage(0);
	}

	/**
	 * A page <b>can</b> be unpinned twice.
	 */
	@Test
	public void testPinUnpin_unpinTwice() {
		int N = allocatePages(pagedFile);
		for (int i = 0; i < N; i++) {
			pagedFile.unpinPage(i);
			pagedFile.unpinPage(i);
		}
	}

	/**
	 * A page can be pinned again by <tt>getPage</tt> after it is unpinned.
	 */
	@Test
	public void testPinUnpin_unpinAndGet() {
		Page page = pagedFile.allocatePage();
		pagedFile.unpinPage(page);
		pagedFile.getPage(page.num);
		// Note: we can only mark a pinned page as dirty.
		pagedFile.markDirty(page);
		pagedFile.unpinPage(page);
	}

	/**
	 * All dirty (unpinned) pages are written back to disk when closing the
	 * paged file.
	 */
	@Test
	public void testWriteBack_dirty_noForce() {
		String data = randomString();
		{
			Page page = pagedFile.allocatePage();
			pagedFile.markDirty(page);
			putStringData(page, data);
			pagedFile.unpinPage(page);
			// Do not force this page.
		}
		reOpen();
		{
			Page page = pagedFile.getPage(0);
			String str = getStringData(page, data.length());
			assertEquals(data, str);
			pagedFile.unpinPage(page);
		}
	}

	/**
	 * All dirty (unpinned) pages are ensured to be written back to disk if
	 * they are flushed via <tt>forcePage</tt>.
	 */
	@Test
	public void testWriteBack_dirty_force() {
		String data = randomString();
		{
			Page page = pagedFile.allocatePage();
			pagedFile.markDirty(page);
			putStringData(page, data);
			pagedFile.forcePage(page.num);
			pagedFile.unpinPage(page);
		}
		reOpen();
		{
			Page page = pagedFile.getPage(0);
			String str = getStringData(page, data.length());
			assertEquals(data, str);
			pagedFile.unpinPage(page);
		}
	}

	/**
	 * A non-dirty page cannot be written back to disk.
	 */
	@Test
	public void testWriteBack_notDirty_noForce() {
		TwoStrings data = randomTwoStrings();
		{
			Page page = pagedFile.allocatePage();
			pagedFile.markDirty(page);
			putStringData(page, data.str1);
			pagedFile.forcePage(page.num);
			pagedFile.unpinPage(page);
		}
		reOpen();
		{
			Page page = pagedFile.getFirstPage();
			putStringData(page, data.str2);
			// no force page here
			pagedFile.unpinPage(page);
		}
		reOpen();
		{
			Page page = pagedFile.getPage(0);
			String str = getStringData(page, data.len);
			assertEquals(data.str1, str);
			pagedFile.unpinPage(page);
		}
	}

	/**
	 * A non-dirty page cannot be flushed to disk via <tt>forcePage</tt>.
	 */
	@Test
	public void testWriteBack_notDirty_force() {
		TwoStrings data = randomTwoStrings();
		{
			Page page = pagedFile.allocatePage();
			pagedFile.markDirty(page);
			putStringData(page, data.str1);
			pagedFile.forcePage(page.num);
			pagedFile.unpinPage(page);
		}
		reOpen();
		{
			Page page = pagedFile.allocatePage();
			// Do not mark page as dirty.
			putStringData(page, data.str2);
			pagedFile.forcePage(page.num);
			pagedFile.unpinPage(page);
		}
		reOpen();
		{
			Page page = pagedFile.getPage(0);
			String str = getStringData(page, data.len);
			assertEquals(data.str1, str);
			pagedFile.unpinPage(page);
		}
	}

	/**
	 * A page is no longer dirty after <tt>forcePage</tt>. The data written
	 * after <tt>forcePage</tt> will not be written back to disk.
	 */
	@Test
	public void testWriteBack_notDirtyAfterForce() {
		TwoStrings data = randomTwoStrings();
		{
			Page page = pagedFile.allocatePage();
			pagedFile.markDirty(page);
			putStringData(page, data.str1);
			pagedFile.forcePage(page.num);
			// The page is no longer dirty.
			putStringData(page, data.str2);
			pagedFile.unpinPage(page);
			// Do not force this page.
		}
		reOpen();
		{
			Page page = pagedFile.getPage(0);
			String str = getStringData(page, data.len);
			assertEquals(data.str1, str);
			pagedFile.unpinPage(page);
		}
	}

}
