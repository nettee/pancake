package me.nettee.pancake.core.page;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;

import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PagedFileTest {

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
	
	public static int allocatePages(PagedFile pagedFile) {
		int N = RandomUtils.nextInt(5, 20);
		for (int __ = 0; __ < N; __++) {
			pagedFile.allocatePage();
		}
		return N;
	}
	
	public static Deque<Integer> disposePages(PagedFile pagedFile, int N) {
		Deque<Integer> disposedPageNums = new LinkedList<>();
		for (int i = 0; i < 3; i++) {
			int pageNum = RandomUtils.nextInt(i * N / 3, (i + 1) * N / 3);
			pagedFile.unpinPage(pageNum);
			pagedFile.disposePage(pageNum);
			disposedPageNums.push(pageNum);
		}
		return disposedPageNums;
	}
	
	public static void putStringData(Page page, String data) {
		byte[] bytes = data.getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(bytes, 0, page.data, 0, bytes.length);
	}

	public static String getStringData(Page page, int length) {
		byte[] bytes = Arrays.copyOfRange(page.data, 0, length);
		String str = new String(bytes, StandardCharsets.US_ASCII);
		return str;
	}


	private int allocatePages() {
	    return allocatePages(pagedFile);
	}

	@Test
	public void testAllocatePage() {
	    // Allocate N pages for a new PagedFile, the page numbers
	    // must be 0, 1, 2, ..., N-1.
		int N = allocatePages();
		for (int pageNum = 0; pageNum < N; pageNum++) {
			Page page = pagedFile.getPage(pageNum);
			assertEquals(pageNum, page.num);
		}
	}

	@Test
	public void testDisposePage() {
		// The page numbers of disposed pages must not exist
		// (until assigned to newly allocated pages).
		int N = allocatePages();
		Iterable<Integer> disposedPageNums = disposePages(pagedFile, N);
		for (int pageNum : disposedPageNums) {
			thrown.expect(PagedFileException.class);
			pagedFile.getPage(pageNum);
		}
	}
	
	@Test
	public void testDisposePage_unpinnedPage() {
	    // A page must be unpinned before disposed, or an exception will be thrown.
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(0, N);
		thrown.expect(PagedFileException.class);
		pagedFile.disposePage(pageNum);
	}
	
	@Test
	public void testDisposePage_notExist() {
		// The number of the page to dispose must exist, or an exception will be thrown.
		int N = allocatePages();
		thrown.expect(PagedFileException.class);
		pagedFile.disposePage(N + 1);
	}
	
	@Test
	public void testDisposePage_disposeTwice() {
		// A page must not be disposed twice, or an exception will be thrown.
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(0, N);
		pagedFile.unpinPage(pageNum);
		pagedFile.disposePage(pageNum);
		thrown.expect(PagedFileException.class);
		pagedFile.disposePage(pageNum);
	}
	
	@Test
	public void testReAllocatePage() {
		// If a page is just disposed, the newly allocated page must have
		// the same number as the disposed page.
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
	
	@Test
	public void testGetPage_disposed() {
		// Getting the disposed page will cause an exception to throw.
		int N = allocatePages(pagedFile);
		Deque<Integer> disposedPageNums = disposePages(pagedFile, N);
		for (int pageNum : disposedPageNums) {
			thrown.expect(PagedFileException.class);
			pagedFile.getPage(pageNum);
		}
	}
	
	@Test
	public void testGetFirstPage() {
		// The number of the first page is zero.
		allocatePages();
		Page firstPage = pagedFile.getFirstPage();
		assertEquals(0, firstPage.num);
	}
	
	@Test
	public void testGetFirstPage_disposeFirstPage() {
		// When getting the first page, disposed pages are omitted.
		// When page[0] is disposed, the first page becomes page[1].
		allocatePages();
		pagedFile.unpinPage(0);
		pagedFile.disposePage(0);
		Page firstPage = pagedFile.getFirstPage();
		assertEquals(1, firstPage.num);
	}
	
	@Test
	public void testGetFirstPage_emptyPagedFile() {
		// When there are no pages, getting the first page will cause an exception.
		thrown.expect(PagedFileException.class);
		pagedFile.getFirstPage();
	}
	
	@Test
	public void testGetFirstPage_disposeToEmpty() {
		// When all the pages are disposed, getting the first page will cause an exception.
		Page page = pagedFile.allocatePage();
		pagedFile.unpinPage(page.num);
		pagedFile.disposePage(page.num);
		thrown.expect(PagedFileException.class);
		pagedFile.getFirstPage();
	}
	
	@Test
	public void testGetLastPage() {
		// The number of the last page is N-1.
		int N = allocatePages();
		Page lastPage = pagedFile.getLastPage();
		assertEquals(N - 1, lastPage.num);
	}
	
	@Test
	public void testGetLastPage_disposeLastPage() {
		// When page[N-1] is disposed, the last page becomes page[N-2].
		int N = allocatePages();
		pagedFile.unpinPage(N - 1);
		pagedFile.disposePage(N - 1);
		Page lastPage = pagedFile.getLastPage();
		assertEquals(N - 2, lastPage.num);
	}
	
	@Test
	public void testGetLastPage_emptyPagedFile() {
		// When there are no pages, getting the last page will cause an exception.
		thrown.expect(PagedFileException.class);
		pagedFile.getLastPage();
	}
	
	@Test
	public void testGetLastPage_disposeToEmpty() {
		// When all the pages are disposed, getting the last page will cause an exception.
		Page page = pagedFile.allocatePage();
		pagedFile.unpinPage(page.num);
		pagedFile.disposePage(page.num);
		thrown.expect(PagedFileException.class);
		pagedFile.getLastPage();
	}
	
	@Test
	public void testGetPreviousPage() {
		// The number of the previous page must be one less than that of the current one.
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(1, N);
		Page previousPage = pagedFile.getPreviousPage(pageNum);
		assertEquals(pageNum - 1, previousPage.num);
	}
	
	@Test
	public void testGetPreviousPage_disposedPageGap() {
		// When finding the previous page, disposed pages are skipped.
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(2, N);
		pagedFile.unpinPage(pageNum - 1);
		pagedFile.disposePage(pageNum - 1);
		Page previousPage = pagedFile.getPreviousPage(pageNum);
		assertEquals(pageNum - 2, previousPage.num);
	}
	
	@Test
	public void testGetPreviousPage_noPrevious() {
		// Page[0] has no previous page, and getting the previous will cause an exception.
		allocatePages();
		thrown.expect(PagedFileException.class);
		pagedFile.getPreviousPage(0);
	}
	
	@Test
	public void testGetNextPage() {
		// The number of the next page must be one larger than that of the current one.
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(0, N - 1);
		Page nextPage = pagedFile.getNextPage(pageNum);
		assertEquals(pageNum + 1, nextPage.num);
	}
	
	@Test
	public void testGetNextPage_disposedPageGap() {
		// When finding the next page, disposed pages are skipped.
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(0, N - 2);
		pagedFile.unpinPage(pageNum + 1);
		pagedFile.disposePage(pageNum + 1);
		Page nextPage = pagedFile.getNextPage(pageNum);
		assertEquals(pageNum + 2, nextPage.num);
	}
	
	@Test
	public void testGetNextPage_noNext() {
		// Page[N-1] has no next page, and getting the next will cause an exception.
		int N = allocatePages();
		thrown.expect(PagedFileException.class);
		pagedFile.getNextPage(N - 1);
	}

}
