package me.nettee.pancake.core.storage;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PageContainerTest {
	
	private static PageContainer pageContainer;
	
	private static void randomPad(PageContainer pc, int lb, int ub) {
		Random random = new Random();
		int padSize = lb + random.nextInt(ub - lb);
		for (int i = 0; i < padSize; i++) {
			pc.allocatePage();
		}
	}
	
	private static void randomPad(PageContainer pc, int lb) {
		randomPad(pc, lb, 200);
	}
	
	private static void randomPad(PageContainer pc) {
		randomPad(pc, 0, 200);
	}
	
	@Before
	public void setUp() {
		pageContainer = new MemoryPageContainer();
	}
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	@Test
	public void testAllocatePage() {
		pageContainer.allocatePage();
	}
	
	@Test
	public void testAllocatePageNum() {
		/*
		 * Each allcated page should have distinct page numbers
		 */
		randomPad(pageContainer);
		int pagesToAllocate = 5;
		Set<Integer> pageNums = new HashSet<Integer>();
		for (int i = 0; i < pagesToAllocate; i++) {
			Page page = pageContainer.allocatePage();
			pageNums.add(page.getPageNum());
		}
		assertEquals(pagesToAllocate, pageNums.size());
	}
	
	@Test
	public void testGetFirstPage_init() {
		/*
		 * There is no first page at the beginning.
		 */
		thrown.expect(NoSuchElementException.class);
		pageContainer.getFirstPage();
	}
	
	@Test
	public void testGetFirstPage() {
		/*
		 * First page is the page allocated in the first place.
		 */
		Page page = pageContainer.allocatePage();
		randomPad(pageContainer);
		Page firstPage = pageContainer.getFirstPage();
		assertEquals(page.getPageNum(), firstPage.getPageNum());
	}
	
	@Test
	public void testGetLastPage_init() {
		/*
		 * There is no last page at the beginning.
		 */
		thrown.expect(NoSuchElementException.class);
		pageContainer.getLastPage();
	}
	
	@Test
	public void testGetLastPage() {
		/*
		 * Last page is the page allocated latest.
		 */
		randomPad(pageContainer);
		Page page = pageContainer.allocatePage();
		Page lastPage = pageContainer.getLastPage();
		assertEquals(page.getPageNum(), lastPage.getPageNum());
	}
	
	@Test
	public void testGetPrevPage() {
		/*
		 * If two pages are allcated one after another, get prev page
		 * of the second page should return the first page.
		 */
		randomPad(pageContainer);
		Page page1 = pageContainer.allocatePage();
		Page page2 = pageContainer.allocatePage();
		randomPad(pageContainer);
		Page prevPageOfPage2 = pageContainer.getPrevPage(page2);
		assertEquals(page1.getPageNum(), prevPageOfPage2.getPageNum());
	}
	
	@Test
	public void testGetPrevPage_firstPage() {
		/*
		 * Get prev page of the first page, should throw exception.
		 */
		randomPad(pageContainer, 1);
		Page firstPage = pageContainer.getFirstPage();
		thrown.expect(NoSuchElementException.class);
		pageContainer.getPrevPage(firstPage);
	}
	
	@Test
	public void testGetNextPage() {
		/*
		 * If two pages are allocated one after another, get next page
		 * of the first page should return the second page.
		 */
		randomPad(pageContainer);
		Page page1 = pageContainer.allocatePage();
		Page page2 = pageContainer.allocatePage();
		randomPad(pageContainer);
		Page nextPageOfPage1 = pageContainer.getNextPage(page1);
		assertEquals(page2.getPageNum(), nextPageOfPage1.getPageNum());
	}
	
	@Test
	public void testGetNextPage_lastPage() {
		/*
		 * Get next page of the last page, should throw exception. 
		 */
		randomPad(pageContainer, 1);
		Page lastPage = pageContainer.getLastPage();
		thrown.expect(NoSuchElementException.class);
		pageContainer.getNextPage(lastPage);
	}
	
	@Test
	public void testGetPrevNextPage() {
		/*
		 * Get next page of prev page, or get prev page of next page,
		 * should return the original page.
		 */
		randomPad(pageContainer, 1);
		Page page = pageContainer.allocatePage();
		randomPad(pageContainer, 1);
		Page nextPrevPage = pageContainer.getNextPage(pageContainer.getPrevPage(page));
		assertEquals(page.getPageNum(), nextPrevPage.getPageNum());
		Page prevNextPage = pageContainer.getPrevPage(pageContainer.getNextPage(page));
		assertEquals(page.getPageNum(), prevNextPage.getPageNum());
	}

}
