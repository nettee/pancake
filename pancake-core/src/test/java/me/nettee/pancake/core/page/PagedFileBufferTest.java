package me.nettee.pancake.core.page;

import static me.nettee.pancake.core.page.PagedFilePageTest.allocatePages;
import static me.nettee.pancake.core.page.PagedFilePageTest.unpinPages;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PagedFileBufferTest {

	private static final File file = new File("/tmp/c.db");
	private PagedFile pagedFile;

	@BeforeClass
	public static void setUpBeforeClass() throws IOException {

	}

	@Before
	public void setUp() throws IOException {
		if (file.exists()) {
			file.delete();
		}
		pagedFile = PagedFile.create(file);
	}

	@After
	public void tearDown() {
		pagedFile.close();
	}

	private void reOpen() {
		pagedFile.close();
		pagedFile = PagedFile.open(file);
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
	public void testBuffer_full() {
		// Make buffer full
		for (int i = 0; i < PageBuffer.BUFFER_SIZE; i++) {
			pagedFile.allocatePage();
		}
		try {
			pagedFile.allocatePage();
			fail("expect PagedFileException to throw");
		} catch (PagedFileException e) {
			// expected
		}
		unpinPages(pagedFile, PageBuffer.BUFFER_SIZE);
	}

	/**
	 * When the buffer is full, unpinned pages will be removed to save space
	 * for newly pinned pages
	 */
	@Test
	public void testBuffer_full_removeUnpinned() {
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
	public void testBuffer_unpin_neverFull() {
		// Allocate pages more than buffer size.
		for (int i = 0; i < 3 * PageBuffer.BUFFER_SIZE; i++) {
			Page page = pagedFile.allocatePage();
			pagedFile.unpinPage(page.num);
		}
	}

	private void putStringData(Page page, String data) {
		byte[] bytes = data.getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(bytes, 0, page.data, 0, bytes.length);
	}

	private String getStringData(Page page, int length) {
		byte[] bytes = Arrays.copyOfRange(page.data, 0, length);
		String str = new String(bytes, StandardCharsets.US_ASCII);
		return str;
	}

	@Test
	public void testForcePage() {
		String str = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		{
			Page page = pagedFile.allocatePage();
			pagedFile.markDirty(page.num);
			putStringData(page, str);
			pagedFile.forcePage(page.num);
			pagedFile.unpinPage(page.num);
		}
		reOpen();
		{
			Page page = pagedFile.getPage(0);
			String str2 = getStringData(page, str.length());
			assertEquals(str, str2);
			pagedFile.unpinPage(page.num);
		}
	}

	@Test
	public void testForcePage_noForce() {
		String str1 = "ABCDEFG-HIJKLMN-OPQRST-UVWXYZ";
		String str2 = "OPQRST-UVWXYZ-ABCDEFG-HIJKLMN";
		{
			Page page = pagedFile.allocatePage();
			pagedFile.unpinPage(page);
		}
		reOpen();
		{
			Page page = pagedFile.getFirstPage();
			pagedFile.markDirty(page.num);
			putStringData(page, str1);
			pagedFile.forcePage(page.num);
			pagedFile.unpinPage(page);
		}
		reOpen();
		{
			Page page = pagedFile.getFirstPage();
			putStringData(page, str2);
			// no force page here
			pagedFile.unpinPage(page);
		}
		reOpen();
		{
			Page page = pagedFile.getFirstPage();
			String str = getStringData(page, str1.length());
			assertEquals(str1, str);
			pagedFile.unpinPage(page);
		}
	}

	// TODO more test cases

}
