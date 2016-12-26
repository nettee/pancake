package me.nettee.pancake.core.page;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
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

	@BeforeClass
	public static void setUpBeforeClass() throws IOException {

	}

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

	private int allocatePages() {
		int N = RandomUtils.nextInt(5, 20);
		for (int i = 0; i < N; i++) {
			pagedFile.allocatePage();
		}
		return N;
	}

	private Deque<Integer> disposePages(int N) {
		Deque<Integer> disposedPageNums = new LinkedList<>();
		for (int i = 0; i < 3; i++) {
			int pageNum = RandomUtils.nextInt(i * N / 3, (i + 1) * N / 3);
			pagedFile.unpinPage(pageNum);
			pagedFile.disposePage(pageNum);
			disposedPageNums.push(pageNum);
		}
		return disposedPageNums;
	}

	@Test
	public void testAllocatePage() {
		int N = allocatePages();
		for (int pageNum = 0; pageNum < N; pageNum++) {
			Page page = pagedFile.getPage(pageNum);
			assertEquals(pageNum, page.num);
		}
	}

	@Test
	public void testDisposePage() {
		int N = allocatePages();
		Iterable<Integer> disposedPageNums = disposePages(N);
		for (int pageNum : disposedPageNums) {
			thrown.expect(PagedFileException.class);
			thrown.expectMessage("disposed");
			pagedFile.getPage(pageNum);
		}
	}
	
	@Test
	public void testDisposePage_notExist() {
		int N = allocatePages();
		thrown.expect(PagedFileException.class);
		pagedFile.disposePage(N + 1);
	}
	
	@Test
	public void testDisposePage_disposeTwice() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(0, N);
		pagedFile.unpinPage(pageNum);
		pagedFile.disposePage(pageNum);
		thrown.expect(PagedFileException.class);
		pagedFile.disposePage(pageNum);
	}
	
	@Test
	public void testReAllocatePage() {
		int N = allocatePages();
		Deque<Integer> disposedPageNums = disposePages(N);
		while (!disposedPageNums.isEmpty()) {
			int expectedPageNum = disposedPageNums.pop();
			Page page = pagedFile.allocatePage();
			assertEquals(expectedPageNum, page.num);
			Page page2 = pagedFile.getPage(expectedPageNum);
			assertEquals(expectedPageNum, page2.num);
		}
	}
	
	@Test
	public void testGetFirstPage() {
		allocatePages();
		Page firstPage = pagedFile.getFirstPage();
		assertEquals(0, firstPage.num);
	}
	
	@Test
	public void testGetFirstPage_disposeFirstPage() {
		allocatePages();
		pagedFile.unpinPage(0);
		pagedFile.disposePage(0);
		Page firstPage = pagedFile.getFirstPage();
		assertEquals(1, firstPage.num);
	}
	
	@Test
	public void testGetFirstPage_emptyPagedFile() {
		thrown.expect(PagedFileException.class);
		pagedFile.getFirstPage();
	}
	
	@Test
	public void testGetFirstPage_disposeToEmpty() {
		Page page = pagedFile.allocatePage();
		pagedFile.unpinPage(page.num);
		pagedFile.disposePage(page.num);
		thrown.expect(PagedFileException.class);
		pagedFile.getFirstPage();
	}
	
	@Test
	public void testGetLastPage() {
		int N = allocatePages();
		Page lastPage = pagedFile.getLastPage();
		assertEquals(N - 1, lastPage.num);
	}
	
	@Test
	public void testGetLastPage_disposeLastPage() {
		int N = allocatePages();
		pagedFile.unpinPage(N - 1);
		pagedFile.disposePage(N - 1);
		Page lastPage = pagedFile.getLastPage();
		assertEquals(N - 2, lastPage.num);
	}
	
	@Test
	public void testGetLastPage_emptyPagedFile() {
		thrown.expect(PagedFileException.class);
		pagedFile.getLastPage();
	}
	
	@Test
	public void testGetLastPage_disposeToEmpty() {
		Page page = pagedFile.allocatePage();
		pagedFile.unpinPage(page.num);
		pagedFile.disposePage(page.num);
		thrown.expect(PagedFileException.class);
		pagedFile.getLastPage();
	}
	
	@Test
	public void testGetPreviousPage() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(1, N);
		Page previousPage = pagedFile.getPreviousPage(pageNum);
		assertEquals(pageNum - 1, previousPage.num);
	}
	
	@Test
	public void testGetPreviousPage_disposedPageGap() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(2, N);
		pagedFile.unpinPage(pageNum - 1);
		pagedFile.disposePage(pageNum - 1);
		Page previousPage = pagedFile.getPreviousPage(pageNum);
		assertEquals(pageNum - 2, previousPage.num);
	}
	
	@Test
	public void testGetPreviousPage_noPrevious() {
		allocatePages();
		thrown.expect(PagedFileException.class);
		pagedFile.getPreviousPage(0);
	}
	
	@Test
	public void testGetNextPage() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(0, N - 1);
		Page nextPage = pagedFile.getNextPage(pageNum);
		assertEquals(pageNum + 1, nextPage.num);
	}
	
	@Test
	public void testGetNextPage_disposedPageGap() {
		int N = allocatePages();
		int pageNum = RandomUtils.nextInt(0, N - 2);
		pagedFile.unpinPage(pageNum + 1);
		pagedFile.disposePage(pageNum + 1);
		Page nextPage = pagedFile.getNextPage(pageNum);
		assertEquals(pageNum + 2, nextPage.num);
	}
	
	@Test
	public void testGetNextPage_noNext() {
		int N = allocatePages();
		thrown.expect(PagedFileException.class);
		pagedFile.getNextPage(N - 1);
	}

}
