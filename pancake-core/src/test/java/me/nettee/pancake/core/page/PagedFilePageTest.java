package me.nettee.pancake.core.page;

import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;

public class PagedFilePageTest {

	private PagedFile pagedFile;

	@Before
	public void setUp() throws IOException {
		File file = new File("/tmp/a.db");
		if (file.exists()) {
			file.delete();
		}
		pagedFile = PagedFile.create(file);
	}

	@After
	public void tearDown() {
		pagedFile.close();
	}

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	/**
	 * Allocate a random number of pages.
	 * @param pagedFile the <tt>PagedFile</tt> object
	 * @return the number of allocated pages
	 */
	static int allocatePages(PagedFile pagedFile) {
		int N = RandomUtils.nextInt(5, 20);
		for (int i = 0; i < N; i++) {
			pagedFile.allocatePage();
		}
		return N;
	}

	/**
	 * Randomly dispose 3 pages, and return their pageNums.
	 * @param pagedFile the <tt>PagedFile</tt> object
	 * @param N the number of existing pages
	 * @return the pageNums of disposed pages
	 */
	static Deque<Integer> disposePages(PagedFile pagedFile, int N) {
		Deque<Integer> disposedPageNums = new LinkedList<>();
		for (int i = 0; i < 3; i++) {
			int pageNum = RandomUtils.nextInt(i * N / 3, (i + 1) * N / 3);
			pagedFile.unpinPage(pageNum);
			pagedFile.disposePage(pageNum);
			disposedPageNums.push(pageNum);
		}
		return disposedPageNums;
	}
	
	static void putStringData(Page page, String data) {
		byte[] bytes = data.getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(bytes, 0, page.data, 0, bytes.length);
	}

	static String getStringData(Page page, int length) {
		byte[] bytes = Arrays.copyOfRange(page.data, 0, length);
		return new String(bytes, StandardCharsets.US_ASCII);
	}


	private int allocatePages() {
	    return allocatePages(pagedFile);
	}

	/**
	 * Allocate N pages for a new <tt>PagedFile</tt>, the page numbers must be
	 * 0, 1, 2, ..., N-1.
	 */
	@Test
	public void testAllocatePage() {
		int N = allocatePages();
		for (int i = 0; i < N; i++) {
			Page page = pagedFile.getPage(i);
			assertEquals(i, page.num);
		}
	}

	/**
	 * The pageNums of disposed pages must not exist (until assigned to newly
	 * allocated pages).
	 */
	@Test
	public void testDisposePage() {
		int N = allocatePages();
		Iterable<Integer> disposedPageNums = disposePages(pagedFile, N);
		for (int pageNum : disposedPageNums) {
			thrown.expect(PagedFileException.class);
			pagedFile.getPage(pageNum);
		}
	}

	/**
	 * A page must be unpinned before disposed.
	 */
	@Test
	public void testDisposePage_unpinnedPage() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(0, N);
		thrown.expect(PagedFileException.class);
		pagedFile.disposePage(pageNum);
	}

	/**
	 * You cannot dispose a nonexistent page.
	 */
	@Test
	public void testDisposePage_notExist() {
		// The number of the page to dispose must exist, or an exception will be thrown.
		int N = allocatePages();
		thrown.expect(PagedFileException.class);
		pagedFile.disposePage(N + 1);
	}

	/**
	 * A page must not be disposed twice.
	 */
	@Test
	public void testDisposePage_disposeTwice() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(0, N);
		pagedFile.unpinPage(pageNum);
		pagedFile.disposePage(pageNum);
		thrown.expect(PagedFileException.class);
		pagedFile.disposePage(pageNum);
	}

	/**
	 * If a page is just disposed, the newly allocated page must have the same
	 * number as the disposed page.
	 */
	@Test
	public void testReAllocatePage() {
		int N = allocatePages();
		Deque<Integer> disposedPageNums = disposePages(pagedFile, N);
		while (!disposedPageNums.isEmpty()) {
			int expectedPageNum = disposedPageNums.pop();
			Page page = pagedFile.allocatePage();
			assertEquals(expectedPageNum, page.num);
			Page page2 = pagedFile.getPage(expectedPageNum);
			assertEquals(expectedPageNum, page2.num);
		}
	}

	/**
	 * You cannot <tt>getPage</tt> with a disposed pageNum.
	 */
	@Test
	public void testGetPage_disposed() {
		int N = allocatePages(pagedFile);
		Deque<Integer> disposedPageNums = disposePages(pagedFile, N);
		for (int pageNum : disposedPageNums) {
			thrown.expect(PagedFileException.class);
			pagedFile.getPage(pageNum);
		}
	}

	// The pageNum of the first page must be zero.
	@Test
	public void testGetFirstPage() {
		allocatePages();
		Page firstPage = pagedFile.getFirstPage();
		assertEquals(0, firstPage.num);
	}

	/**
	 * When getting the first page, disposed pages are omitted.
	 * When page[0] is disposed, the first page becomes page[1].
	 */
	@Test
	public void testGetFirstPage_disposeFirstPage() {
		allocatePages();
		pagedFile.unpinPage(0);
		pagedFile.disposePage(0);
		Page firstPage = pagedFile.getFirstPage();
		assertEquals(1, firstPage.num);
	}

	/**
	 * You cannot get the first page when there are no pages.
	 */
	@Test
	public void testGetFirstPage_emptyPagedFile() {
		thrown.expect(PagedFileException.class);
		pagedFile.getFirstPage();
	}

	/**
	 * You cannot get the first page when all pages have been disposed.
	 */
	@Test
	public void testGetFirstPage_disposeToEmpty() {
		Page page = pagedFile.allocatePage();
		pagedFile.unpinPage(page.num);
		pagedFile.disposePage(page.num);
		thrown.expect(PagedFileException.class);
		pagedFile.getFirstPage();
	}

	/**
	 * The pageNum of the last page is N-1.
	 */
	@Test
	public void testGetLastPage() {
		int N = allocatePages();
		Page lastPage = pagedFile.getLastPage();
		assertEquals(N - 1, lastPage.num);
	}

	/**
	 * When page[N-1] is disposed, the last page becomes page[N-2].
	 */
	@Test
	public void testGetLastPage_disposeLastPage() {
		int N = allocatePages();
		pagedFile.unpinPage(N - 1);
		pagedFile.disposePage(N - 1);
		Page lastPage = pagedFile.getLastPage();
		assertEquals(N - 2, lastPage.num);
	}

	/**
	 * You cannot get the last page when there are no pages.
	 */
	@Test
	public void testGetLastPage_emptyPagedFile() {
		thrown.expect(PagedFileException.class);
		pagedFile.getLastPage();
	}

	/**
	 * You cannot get the last page when all pages have been disposed.
	 */
	@Test
	public void testGetLastPage_disposeToEmpty() {
		Page page = pagedFile.allocatePage();
		pagedFile.unpinPage(page.num);
		pagedFile.disposePage(page.num);
		thrown.expect(PagedFileException.class);
		pagedFile.getLastPage();
	}

	/**
	 * The pageNum of the previous page must be one less than the current
	 * pageNum.
	 */
	@Test
	public void testGetPreviousPage() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(1, N);
		Page previousPage = pagedFile.getPreviousPage(pageNum);
		assertEquals(pageNum - 1, previousPage.num);
	}

	/**
	 * Disposed pages are skipped when searching for the previous page.
	 */
	@Test
	public void testGetPreviousPage_disposedPageGap() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(2, N);
		pagedFile.unpinPage(pageNum - 1);
		pagedFile.disposePage(pageNum - 1);
		Page previousPage = pagedFile.getPreviousPage(pageNum);
		assertEquals(pageNum - 2, previousPage.num);
	}

	/**
	 * The first page has no previous page.
	 */
	@Test
	public void testGetPreviousPage_noPrevious() {
		allocatePages();
		thrown.expect(PagedFileException.class);
		pagedFile.getPreviousPage(0);
	}

	/**
	 * The pageNum of the next page must be one larger than the current
	 * pageNum.
	 */
	@Test
	public void testGetNextPage() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(0, N - 1);
		Page nextPage = pagedFile.getNextPage(pageNum);
		assertEquals(pageNum + 1, nextPage.num);
	}

	/**
	 * Disposed pages are skipped when searching for the next page.
	 */
	@Test
	public void testGetNextPage_disposedPageGap() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(0, N - 2);
		pagedFile.unpinPage(pageNum + 1);
		pagedFile.disposePage(pageNum + 1);
		Page nextPage = pagedFile.getNextPage(pageNum);
		assertEquals(pageNum + 2, nextPage.num);
	}

	/**
	 * The last page has no next page.
	 */
	@Test
	public void testGetNextPage_noNext() {
		int N = allocatePages();
		thrown.expect(PagedFileException.class);
		pagedFile.getNextPage(N - 1);
	}

	/**
	 * The previous page of the next page is the current page, and so is the
	 * next page of the previous page.
	 */
	@Test
	public void testGetPreviousNextPage() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(1, N - 1);
		Page nextPage = pagedFile.getNextPage(pageNum);
		Page previousOfNextPage = pagedFile.getPreviousPage(nextPage.num);
		assertEquals(pageNum, previousOfNextPage.num);
		Page previousPage = pagedFile.getPreviousPage(pageNum);
		Page nextOfPreviousPage = pagedFile.getNextPage(previousPage.num);
		assertEquals(pageNum, nextOfPreviousPage.num);
	}

	/**
	 * The previous page of the next page is the current page, and so is the
	 * next page of the previous page (disposed pages are skipped).
	 */
	@Test
	public void testGetPreviousNextPage_disposedPageGap() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(2, N - 2);
		pagedFile.unpinPage(pageNum - 1);
		pagedFile.disposePage(pageNum - 1);
		pagedFile.unpinPage(pageNum + 1);
		pagedFile.disposePage(pageNum + 1);
		Page nextPage = pagedFile.getNextPage(pageNum);
		Page previousOfNextPage = pagedFile.getPreviousPage(nextPage.num);
		assertEquals(pageNum, previousOfNextPage.num);
		Page previousPage = pagedFile.getPreviousPage(pageNum);
		Page nextOfPreviousPage = pagedFile.getNextPage(previousPage.num);
		assertEquals(pageNum, nextOfPreviousPage.num);
	}
}
