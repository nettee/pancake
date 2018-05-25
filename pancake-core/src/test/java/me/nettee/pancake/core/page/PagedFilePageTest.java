package me.nettee.pancake.core.page;

import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

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

	static void unpinPages(PagedFile pagedFile, int N) {
		for (int i = 0; i < N; i++) {
			pagedFile.unpinPage(i);
		}
	}

	static void unpinPages(PagedFile pagedFile, Collection<Integer> nums) {
		for (int num : nums) {
			pagedFile.unpinPage(num);
		}
	}

	static void unpinPages(PagedFile pagedFile, int N, Collection<Integer> excepts) {
		for (int i = 0; i < N; i++) {
			if (excepts.contains(i)) {
				continue;
			}
			pagedFile.unpinPage(i);
		}
	}
	
	static void putStringData(Page page, String data) {
		byte[] bytes = data.getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(bytes, 0, page.data, 0, bytes.length);
	}

	static String getStringData(Page page, int length) {
		byte[] bytes = Arrays.copyOfRange(page.data, 0, length);
		return new String(bytes, StandardCharsets.US_ASCII);
	}


	/**
	 * Allocate N pages for a new <tt>PagedFile</tt>, the page numbers must be
	 * 0, 1, 2, ..., N-1.
	 */
	@Test
	public void testAllocatePage() {
		int N = allocatePages(pagedFile);
		for (int i = 0; i < N; i++) {
			Page page = pagedFile.getPage(i);
			assertEquals(i, page.num);
		}
		unpinPages(pagedFile, N);
	}

	/**
	 * The pageNums of disposed pages must not exist (until assigned to newly
	 * allocated pages).
	 */
	@Test
	public void testDisposePage() {
		int N = allocatePages(pagedFile);
		Deque<Integer> disposedPageNums = disposePages(pagedFile, N);
		for (int pageNum : disposedPageNums) {
			try {
				pagedFile.disposePage(pageNum);
				fail("expect PagedFileException to throw");
			} catch (PagedFileException e) {
				// expected
			}
		}
		unpinPages(pagedFile, N);
	}

	/**
	 * A page must be unpinned before disposed.
	 */
	@Test
	public void testDisposePage_unpinnedPage() {
		int N = allocatePages(pagedFile);
		int pageNum = RandomUtils.nextInt(0, N);
		try {
			pagedFile.disposePage(pageNum);
			fail("expect PagedFileException to throw");
		} catch (PagedFileException e) {
			// expected
		}
		unpinPages(pagedFile, N);
	}

	/**
	 * You cannot dispose a nonexistent page.
	 */
	@Test
	public void testDisposePage_notExist() {
		int N = allocatePages(pagedFile);
		try {
			pagedFile.disposePage(N + 1);
			fail("expect PagedFileException to throw");
		} catch (PagedFileException e) {
			// expected
		}
		unpinPages(pagedFile, N);
	}

	/**
	 * A page must not be disposed twice.
	 */
	@Test
	public void testDisposePage_disposeTwice() {
		int N = allocatePages(pagedFile);
		int pageNum = RandomUtils.nextInt(0, N);
		pagedFile.unpinPage(pageNum);
		pagedFile.disposePage(pageNum);
		try {
			pagedFile.disposePage(pageNum);
			fail("expect PagedFileException to throw");
		} catch (PagedFileException e) {
			// expected
		}
		unpinPages(pagedFile, N, Arrays.asList(pageNum));
	}

	/**
	 * If a page is just disposed, the newly allocated page must have the same
	 * number as the disposed page.
	 */
	@Test
	public void testReAllocatePage() {
		int N = allocatePages(pagedFile);
		System.out.println(String.format("allocated %d pages", N));
		Deque<Integer> disposedPageNums = disposePages(pagedFile, N);
		System.out.printf("disposed page nums = %s\n",
				disposedPageNums.stream()
						.map(String::valueOf)
						.collect(Collectors.joining(", ", "[", "]")));
		while (!disposedPageNums.isEmpty()) {
			int expectedPageNum = disposedPageNums.pop();
			Page page = pagedFile.allocatePage();
			assertEquals(expectedPageNum, page.num);
			Page page2 = pagedFile.getPage(expectedPageNum);
			assertEquals(expectedPageNum, page2.num);
		}
		unpinPages(pagedFile, N);
	}

	/**
	 * You cannot <tt>getPage</tt> with a disposed pageNum.
	 */
	@Test
	public void testGetPage_disposed() {
		int N = allocatePages(pagedFile);
		Deque<Integer> disposedPageNums = disposePages(pagedFile, N);
		for (int pageNum : disposedPageNums) {
			try {
				pagedFile.getPage(pageNum);
				fail("expect PagedFileException to throw");
			} catch (PagedFileException e) {
				// expected
			}
		}
		unpinPages(pagedFile, N, disposedPageNums);
	}

	// The pageNum of the first page must be zero.
	@Test
	public void testGetFirstPage() {
		int N = allocatePages(pagedFile);
		Page firstPage = pagedFile.getFirstPage();
		assertEquals(0, firstPage.num);
		unpinPages(pagedFile, N);
	}

	/**
	 * When getting the first page, disposed pages are omitted.
	 * When page[0] is disposed, the first page becomes page[1].
	 */
	@Test
	public void testGetFirstPage_disposeFirstPage() {
		int N = allocatePages(pagedFile);
		pagedFile.unpinPage(0);
		pagedFile.disposePage(0);
		Page firstPage = pagedFile.getFirstPage();
		assertEquals(1, firstPage.num);
		unpinPages(pagedFile, N, Arrays.asList(0));
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
		int N = allocatePages(pagedFile);
		Page lastPage = pagedFile.getLastPage();
		assertEquals(N - 1, lastPage.num);
		unpinPages(pagedFile, N);
	}

	/**
	 * When page[N-1] is disposed, the last page becomes page[N-2].
	 */
	@Test
	public void testGetLastPage_disposeLastPage() {
		int N = allocatePages(pagedFile);
		pagedFile.unpinPage(N - 1);
		pagedFile.disposePage(N - 1);
		Page lastPage = pagedFile.getLastPage();
		assertEquals(N - 2, lastPage.num);
		unpinPages(pagedFile, N, Arrays.asList(N - 1));
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
		int N = allocatePages(pagedFile);
		int pageNum = RandomUtils.nextInt(1, N);
		Page previousPage = pagedFile.getPreviousPage(pageNum);
		assertEquals(pageNum - 1, previousPage.num);
		unpinPages(pagedFile, N);
	}

	/**
	 * Disposed pages are skipped when searching for the previous page.
	 */
	@Test
	public void testGetPreviousPage_disposedPageGap() {
		int N = allocatePages(pagedFile);
		int pageNum = RandomUtils.nextInt(2, N);
		pagedFile.unpinPage(pageNum - 1);
		pagedFile.disposePage(pageNum - 1);
		Page previousPage = pagedFile.getPreviousPage(pageNum);
		assertEquals(pageNum - 2, previousPage.num);
		unpinPages(pagedFile, N, Arrays.asList(pageNum - 1));
	}

	/**
	 * The first page has no previous page.
	 */
	@Test
	public void testGetPreviousPage_noPrevious() {
		int N = allocatePages(pagedFile);
		try {
			pagedFile.getPreviousPage(0);
			fail("expect PagedFileException to throw");
		} catch (PagedFileException e) {
			// expected
		}
		unpinPages(pagedFile, N);
	}

	/**
	 * The pageNum of the next page must be one larger than the current
	 * pageNum.
	 */
	@Test
	public void testGetNextPage() {
		int N = allocatePages(pagedFile);
		int pageNum = RandomUtils.nextInt(0, N - 1);
		Page nextPage = pagedFile.getNextPage(pageNum);
		assertEquals(pageNum + 1, nextPage.num);
		unpinPages(pagedFile, N);
	}

	/**
	 * Disposed pages are skipped when searching for the next page.
	 */
	@Test
	public void testGetNextPage_disposedPageGap() {
		int N = allocatePages(pagedFile);
		int pageNum = RandomUtils.nextInt(0, N - 2);
		pagedFile.unpinPage(pageNum + 1);
		pagedFile.disposePage(pageNum + 1);
		Page nextPage = pagedFile.getNextPage(pageNum);
		assertEquals(pageNum + 2, nextPage.num);
		unpinPages(pagedFile, N , Arrays.asList(pageNum + 1));
	}

	/**
	 * The last page has no next page.
	 */
	@Test
	public void testGetNextPage_noNext() {
		int N = allocatePages(pagedFile);
		try {
			pagedFile.getNextPage(N - 1);
			fail("expect PagedFileException to throw");
		} catch (PagedFileException e) {
			// expected
		}
		unpinPages(pagedFile, N);
	}

	/**
	 * The previous page of the next page is the current page, and so is the
	 * next page of the previous page.
	 */
	@Test
	public void testGetPreviousNextPage() {
		int N = allocatePages(pagedFile);
		int pageNum = RandomUtils.nextInt(1, N - 1);
		Page nextPage = pagedFile.getNextPage(pageNum);
		Page previousOfNextPage = pagedFile.getPreviousPage(nextPage.num);
		assertEquals(pageNum, previousOfNextPage.num);
		Page previousPage = pagedFile.getPreviousPage(pageNum);
		Page nextOfPreviousPage = pagedFile.getNextPage(previousPage.num);
		assertEquals(pageNum, nextOfPreviousPage.num);
		unpinPages(pagedFile, N);
	}

	/**
	 * The previous page of the next page is the current page, and so is the
	 * next page of the previous page (disposed pages are skipped).
	 */
	@Test
	public void testGetPreviousNextPage_disposedPageGap() {
		int N = allocatePages(pagedFile);
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
		unpinPages(pagedFile, N, Arrays.asList(pageNum - 1, pageNum + 1));
	}
}
