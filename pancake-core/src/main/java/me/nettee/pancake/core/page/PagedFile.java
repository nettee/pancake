package me.nettee.pancake.core.page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static com.google.common.base.Preconditions.*;
import static java.nio.file.StandardOpenOption.*;

/**
 * <b>Paged file</b> is the bottom component of pancake-core. This component
 * provides facilities for higher-level client components to perform file I/O in
 * terms of pages.
 * <p>
 * <b>Page number</b> identifies a page in a paged file. Page numbers correspond
 * to their location within the file on disk. When you initially create a file
 * and allocate pages using {@linkplain PagedFile#allocatePage()
 * allocatePage()}, page numbering will be sequential. However, once pages have
 * been deleted, the numbers of newly allocated pages are not sequential. The
 * paged file reallocates previously allocated pages using a LIFO (stack)
 * algorithm -- that is it reallocates the most recently deleted (and not
 * reallocated) page. A brand new page is never allocated if a previously
 * allocated page is available.
 * <p>
 *
 * @author nettee
 *
 */
public class PagedFile {

	private static Logger logger = LoggerFactory.getLogger(PagedFile.class);

	private FileChannel file;
	private int N; // Number of pages
//	private Map<Integer, Integer> disposedPageIndexes = new HashMap<>();
	private Deque<Integer> disposedPageNumsStack = new LinkedList<>();

	private PageBuffer buffer;

	private PagedFile(Path path) {
		buffer = new PageBuffer(this);
		try {
			this.file = FileChannel.open(path, CREATE, READ, WRITE);
		} catch (IOException e) {
			throw new PagedFileException(e);
		}
	}

	/**
	 * Create a paged file. The file should not already exist.
	 * 
	 * @param path
	 *            the path of database file
	 * @return created paged file
	 */
	public static PagedFile create(Path path) {
		checkNotNull(path);
		checkArgument(Files.notExists(path), "file already exists: %s", path.toString());
		logger.info("Creating PagedFile {}", path.toString());
		PagedFile pagedFile = new PagedFile(path);
		pagedFile.initPages();
		return pagedFile;
	}

	/**
	 * Open a paged file. The file must already exist and have been created
	 * using the <tt>create</tt> method.
	 * 
	 * @param path
	 *            the path of database file
	 * @return opened paged file
	 * @throws PagedFileException
	 */
	public static PagedFile open(Path path) {
		checkNotNull(path);
		checkArgument(Files.exists(path), "file does not exist: %s", path.toString());
		logger.info("Opening PagedFile {}", path.toString());
		PagedFile pagedFile = new PagedFile(path);
		try {
			pagedFile.loadPages();
		} catch (IOException e) {
			throw new PagedFileException(e);
		}
		return pagedFile;
	}

	private void initPages() {
		N = 0;
	}

	private void loadPages() throws IOException {
		if (file.size() % Page.PAGE_SIZE != 0) {
			logger.warn("file length is not dividable by {}", Page.PAGE_SIZE);
		}
		N = (int) (file.size() / Page.PAGE_SIZE);

		// Restore the order of disposed pages. Their pageNums are pushed
		// orderly into the stack.
		int[] disposedPageNums = new int[N+1];
		for (int i = 0; i <= N; i++) {
			disposedPageNums[i] = -1;
		}
		for (int pageNum = 0; pageNum < N; pageNum++) {
			file.position(pageNum * Page.PAGE_SIZE);
			ByteBuffer pageNumCopy = ByteBuffer.allocate(4);
			file.read(pageNumCopy);
			pageNumCopy.flip();
			int actualNum = pageNumCopy.asIntBuffer().get(0);
			if (actualNum < 0) {
				// A disposed page has its page number less than zero.
                int disposedOrder = -actualNum;
                disposedPageNums[disposedOrder] = pageNum;
			}
		}
		for (int pageNum : disposedPageNums) {
			if (pageNum >= 0) {
				disposedPageNumsStack.push(pageNum);
			}
		}
	}

	/**
	 * Close the paged file. All of the pages are flushed from the buffer pool
	 * to the disk before the file is closed.
	 */
	public void close() {
		logger.info("Closing PagedFile");
		if (buffer.hasPinnedPages()) {
			logger.error("Still has pinned pages[{}]", Pages.pageRangeRepr(buffer.getPinnedPages()));
			throw new PagedFileException("Fail to close paged file: there are pinned pages in the buffer pool");
		}

		Set<Integer> unpinnedPages = buffer.getUnpinnedPages();
		for (int pageNum : unpinnedPages) {
			writeBack(buffer.get(pageNum));
		}
		if (!unpinnedPages.isEmpty()) {
			logger.info("Written back unpinned pages[{}]", Pages.pageRangeRepr(unpinnedPages));
		}


		try {
			file.close();
		} catch (IOException e) {
			throw new PagedFileException(e);
		}
	}

	public int getNumOfPages() {
		return N;
	}

	private void checkPageNumRange(int pageNum) {
		if (pageNum < 0 || pageNum >= N) {
			throw new PagedFileException("page index out of bound: " + pageNum);
		}
	}

	private boolean isDisposed(int pageNum) {
		return disposedPageNumsStack.contains(pageNum);
	}

	private Page readPageFromFile(int pageNum) throws IOException {
		Page page = new Page(pageNum);
		file.position(pageNum * Page.PAGE_SIZE);
		ByteBuffer pageCopy = ByteBuffer.allocate(Page.PAGE_SIZE);
		file.read(pageCopy);
		pageCopy.position(4);
		pageCopy.get(page.data);
		return page;
	}

	void writePageToFile(Page page) throws IOException {
		file.position(page.num * Page.PAGE_SIZE);
		ByteBuffer out = ByteBuffer.allocate(Page.PAGE_SIZE);
		out.putInt(page.num);
		out.put(page.data);
		out.flip();
		while (out.hasRemaining()) {
			file.write(out);
		}
	}

	/**
	 * Allocate a new page in the file.
	 * 
	 * @return a <tt>Page</tt> object
	 * @throws PagedFileException When it fails to write the file.
	 */
	public Page allocatePage() {
		int pageNum;
		if (disposedPageNumsStack.isEmpty()) {
			pageNum = N++;
		} else {
			pageNum = disposedPageNumsStack.pop();
		}
		logger.info("Allocating page[{}]", pageNum);
		Page page = new Page(pageNum);
		try {
			writePageToFile(page);
		} catch (IOException e) {
			String msg = String.format("fail to allocate page[%d]", pageNum);
			throw new PagedFileException(msg, e);
		}
		try {
			buffer.putAndPin(page);
		} catch (FullBufferException e) {
			logger.error(String.format("Fail to allocate page[%d]", page.num), e);
			throw e;
		}
		return page;
	}

	/**
	 * Remove the page specified by <tt>pageNum</tt>.
     * A page must be unpinned before disposed.
	 * 
	 * @param pageNum the number of the page to dispose
     * @throws PagedFileException When the page number is not exist,
	 * when the page is not unpinned,
	 * or when it fails to write the file.
	 */
	public void disposePage(int pageNum) {
		checkPageNumRange(pageNum);
		if (disposedPageNumsStack.contains(pageNum)) {
			// This page is already disposed.
			String msg = String.format("page[%d] already disposed", pageNum);
			throw new PagedFileException(msg);
		}
		if (buffer.contains(pageNum)) {
			if (buffer.isPinned(pageNum)) {
				String msg = String.format("cannot dispose a pinned page[%d]",
						pageNum);
				throw new PagedFileException(msg);
			}
			buffer.removeWithoutWriteBack(pageNum); // can throw exception
		}
		try {
			file.position(pageNum * Page.PAGE_SIZE);
			// Write at the pageNum position with the opposite number of the
			// disposed order, so that (1) we can identify disposed pages with
			// a negative pageNum; (2) we can restore the disposed order when
			// re-open the file.
			ByteBuffer out = ByteBuffer.allocate(Page.PAGE_SIZE);
			out.putInt(-1 - disposedPageNumsStack.size());
			// Fill the file with default bytes for ease of debugging.
			out.put(Pages.makeDefaultBytes(Page.DATA_SIZE));
			out.flip();
			while (out.hasRemaining()) {
				file.write(out);
			}
		} catch (IOException e) {
			String msg = String.format("fail to dispose page[%d]", pageNum);
			throw new PagedFileException(msg, e);
		}
		disposedPageNumsStack.push(pageNum);
		logger.debug(String.format("dispose page[%d]", pageNum));
	}

	/**
	 * Read page from buffer or from file.
	 * 
	 * NOTE: This method does not check page number range or disposed page.
	 * 
	 * If the page is in buffer, return it simply. Otherwise, read the page from
	 * file and put it into buffer.
	 */
	private Page readPage(int pageNum) {
		try {
			if (buffer.contains(pageNum)) {
				Page page = buffer.get(pageNum);
				// If the page is unpinned before, pin it again.
				buffer.pinAgainIfNot(page);
				return page;
			} else {
				Page page = readPageFromFile(pageNum);
				buffer.putAndPin(page);
				return page;
			}
		} catch (IOException e) {
			String msg = String.format("fail to read page[%d]", pageNum);
			throw new PagedFileException(msg);
		}
	}

	public Page getPage(int pageNum) {
		checkPageNumRange(pageNum);
		if (isDisposed(pageNum)) {
			String msg = String.format("cannot get a disposed page[%d]", pageNum);
			throw new PagedFileException(msg);
		}
		Page page = readPage(pageNum);
		logger.info("Got page[{}]", pageNum);
		return page;
	}

	private Page searchPage(int startPageNum, Predicate<Integer> endPredicate,
							UnaryOperator<Integer> next, String messageOnFail) {
		int pageNum = startPageNum;
		while (!endPredicate.test(pageNum)) {
			if (!isDisposed(pageNum)) {
				return readPage(pageNum);
			}
			pageNum = next.apply(pageNum);
		}
		// no page matches
		throw new PagedFileException(messageOnFail);
	}

	private Page searchPageIncreasing(int startPageNum, int endPageNum, String messageOnFail) {
		return searchPage(startPageNum, x -> x > endPageNum, x -> x + 1, messageOnFail);
	}

	private Page searchPageDecreasing(int startPageNum, int endPageNum, String messageOnFail) {
		return searchPage(startPageNum, x -> x < endPageNum, x -> x - 1, messageOnFail);
	}

	public Page getFirstPage() {
		Page page = searchPageIncreasing(0, N - 1, "no first page");
		logger.info("Got page[{}] (first page)", page.num);
		return page;
	}

	public Page getLastPage() {
		Page page = searchPageDecreasing(N - 1, 0, "no last page");
		logger.info("Got page[{}] (last page)", page.num);
		return page;
	}

	public Page getPreviousPage(int currentPageNum) {
		Page page = searchPageDecreasing(currentPageNum - 1, 0, "no previous page");
		logger.info("Got page[{}] (previous page of page[{}]", page.num, currentPageNum);
		return page;
	}

	public Page getNextPage(int currentPageNum) {
		Page page = searchPageIncreasing(currentPageNum + 1, N - 1, "no next page");
		logger.info("Got page[{}] (next page of page[{}]", page.num, currentPageNum);
		return page;
	}

	/**
	 * Mark that the page specified by <tt>pageNum</tt> have been or will be
	 * modified. The page must be pinned in the buffer pool. The <i>dirty</i>
	 * pages will be written back to disk when removed from the buffer pool.
	 * @param pageNum page number of the page to mark as dirty
	 */
	public void markDirty(int pageNum) {
		checkPageNumRange(pageNum);
		if (!buffer.contains(pageNum)) {
			String msg = String.format("Try to mark page[%d] as dirty which is not in buffer pool", pageNum);
			logger.error(msg);
			throw new PagedFileException(msg);
		}
		Page page = buffer.get(pageNum);
		if (!page.pinned) {
			String msg = String.format("Try to mark an unpinned page[%d] as dirty", pageNum);
			logger.error(msg);
			throw new PagedFileException(msg);
		}
		page.dirty = true;
		logger.info("Marked page[{}] as dirty in buffer", page.num);
	}

	/**
	 * Mark that the <tt>page</tt> have been or will be modified. A
	 * <i>dirty</i> page is written back to disk when it is removed from the
	 * buffer pool.
	 * @param page the page to mark as dirty
	 */
	public void markDirty(Page page) {
		markDirty(page.num);
	}

	/**
	 * Mark that the page specified by <tt>pageNum</tt> is no longer needed in
	 * memory.
	 * @param pageNum page number of the page to unpin
	 */
	public void unpinPage(int pageNum) {
		checkPageNumRange(pageNum);
		if (buffer.contains(pageNum)) {
			buffer.unpin(pageNum);
			logger.info("Unpinned page[{}] in buffer", pageNum);
		}
	}

	/**
	 * Mark that the <tt>page</tt> is no longer need in memory.
	 * @param page the page to unpin
	 */
	public void unpinPage(Page page) {
		unpinPage(page.num);
	}

	void writeBack(Page page) {
		checkState(buffer.contains(page.num));
		if (page.dirty) {
			try {
				writePageToFile(page);
			} catch (IOException e) {
				throw new PagedFileException(e);
			}
		}
	}

	/**
	 * Mark that the pages specified by <tt>pageNums</tt> is no longer needed
	 * in memory.
	 * @param pageNums page numbers of the pages to unpin
	 */
	public void unpinPages(Set<Integer> pageNums) {
		Set<Integer> unpinnedPageNums = new HashSet<>();
		for (int pageNum : pageNums) {
			checkPageNumRange(pageNum);
			if (buffer.contains(pageNum)) {
				buffer.unpin(pageNum);
				unpinnedPageNums.add(pageNum);
			}
		}
		logger.info("Unpinned pages[{}] in buffer", Pages.pageRangeRepr(unpinnedPageNums));
	}

	void writeBack(int pageNum) {
		checkState(buffer.contains(pageNum));
		writeBack(buffer.get(pageNum));
	}

	private void forcePage0(int pageNum) {
		if (!buffer.contains(pageNum)) {
			String msg = String.format(
					"Fail to force page[%d]: page not in buffer pool", pageNum);
			logger.error(msg);
			throw new PagedFileException(msg);
		}
		writeBack(pageNum);

		Page page = buffer.get(pageNum);
		page.dirty = false;
	}

	/**
	 * This method copies the contents of the page specified by <tt>pageNum</tt>
	 * from the buffer pool to disk if the page is in the buffer pool and is
	 * marked as dirty. The page remains in the buffer pool but is no longer
	 * marked as dirty.
	 * 
	 * @param pageNum page number
	 * @throws PagedFileException
	 */
	public void forcePage(int pageNum) {
		forcePage0(pageNum);
		logger.info("Forced page[{}]", pageNum);
	}

	/**
	 * This method copies the contents of the <tt>page</tt> from the buffer
	 * pool to disk if the page is in the buffer pool and is marked as dirty.
	 * The page remains in the buffer pool but is no longer marked as dirty.
	 *
	 * @param page the <tt>Page</tt> object
	 * @throws PagedFileException
	 */
	public void forcePage(Page page) {
		forcePage0(page.num);
		logger.info("Forced page[{}]", page.num);
	}

	/**
	 * This method copies the contents of all the pages in the buffer pool to
	 * disk. The pages remains in the buffer pool but is no longer marked as
	 * dirty. Calling this method has the same effect as calling
	 * <tt>forcePage</tt> on each page.
	 *
	 * @throws PagedFileException
	 */
	public void forceAllPages() {
		// Force all the pages in the buffer pool.
		Set<Integer> allPages = buffer.getAllPages();
		for (int pageNum : allPages) {
			forcePage0(pageNum);
		}
		logger.info("Forced all pages[{}]", Pages.pageRangeRepr(allPages));
	}

}
