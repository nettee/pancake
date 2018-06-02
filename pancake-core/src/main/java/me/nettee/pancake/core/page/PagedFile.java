package me.nettee.pancake.core.page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.*;

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

	private RandomAccessFile file;
	private int N; // Number of pages
//	private Map<Integer, Integer> disposedPageIndexes = new HashMap<>();
	private Deque<Integer> disposedPageNumsStack = new LinkedList<>();

	private PageBuffer buffer;

	private PagedFile(File file) {
		buffer = new PageBuffer(this);
		try {
			this.file = new RandomAccessFile(file, "rw");
		} catch (FileNotFoundException e) {
			throw new PagedFileException(e);
		}
	}

	/**
	 * Create a paged file. The file should not already exist.
	 * 
	 * @param file
	 *            the file in OS
	 * @return created paged file
	 */
	public static PagedFile create(File file) {
		checkNotNull(file);
		checkArgument(!file.exists(), "file already exists: %s", file.getAbsolutePath());
		PagedFile pagedFile = new PagedFile(file);
		pagedFile.initPages();
		return pagedFile;
	}

	/**
	 * Open a paged file. The file must already exist and have been created
	 * using the <tt>create</tt> method.
	 * 
	 * @param file
	 *            the file in OS
	 * @return opened paged file
	 * @throws PagedFileException
	 */
	public static PagedFile open(File file) {
		checkNotNull(file);
		checkArgument(file.exists(), "file does not exist: %s", file.getAbsolutePath());
		PagedFile pagedFile = new PagedFile(file);
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
		if (file.length() % Page.PAGE_SIZE != 0) {
			logger.warn("file length is not dividable by " + Page.PAGE_SIZE);
		}
		N = (int) (file.length() / Page.PAGE_SIZE);

		// Restore the order of disposed pages. Their pageNums are pushed
		// orderly into the stack.
		int[] disposedPageNums = new int[N+1];
		for (int i = 0; i <= N; i++) {
			disposedPageNums[i] = -1;
		}
		for (int pageNum = 0; pageNum < N; pageNum++) {
			file.seek(pageNum * Page.PAGE_SIZE);
			int actualNum = file.readInt();
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
		if (buffer.hasPinnedPages()) {
			System.out.printf("still has pinned pages: %s\n",
					buffer.getPinnedPages().stream()
							.map(String::valueOf)
							.collect(Collectors.joining(", ", "[", "]")));
			throw new PagedFileException("cannot close paged file: there are pinned pages in the buffer pool");
		}

		for (int pageNum : buffer.getUnpinnedPages()) {
			writeBack(buffer.get(pageNum));
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
		file.seek(pageNum * Page.PAGE_SIZE);
		file.readInt();
		file.read(page.data);
		return page;
	}

	void writePageToFile(Page page) throws IOException {
		int startPointer = page.num * Page.PAGE_SIZE;
		int endPointer = (page.num + 1) * Page.PAGE_SIZE;
		file.seek(startPointer);
		file.writeInt(page.num);
		file.write(page.data);
		checkState(file.getFilePointer() == endPointer,
				String.format("error state when writing page[%d]", page.num));
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
		Page page = new Page(pageNum);
		try {
			writePageToFile(page);
		} catch (IOException e) {
			String msg = String.format("fail to allocate page[%d]", pageNum);
			throw new PagedFileException(msg, e);
		}
		buffer.putAndPin(page);
		logger.debug(String.format("allocate page[%d]", pageNum));
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
			file.seek(pageNum * Page.PAGE_SIZE);
			// Write at the pageNum position with the opposite number of the
			// disposed order, so that (1) we can identify disposed pages with
			// a negative pageNum; (2) we can restore the disposed order when
			// re-open the file.
			file.writeInt(-1 - disposedPageNumsStack.size());
			// Fill the file with default bytes for ease of debugging.
			byte[] data = new byte[Page.DATA_SIZE];
			Arrays.fill(data, Page.DEFAULT_BYTE);
			file.write(data);
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
		return readPage(pageNum);
	}

	private Page searchPage(int startPageNum, int endPageNum, UnaryOperator<Integer> next, String messageOnFail) {
		int pageNum = startPageNum;
		// this loop search all pages except endPage
		while (pageNum != endPageNum) {
			if (!isDisposed(pageNum)) {
				return readPage(pageNum);
			}
			pageNum = next.apply(pageNum);
		}
		// examine endPage
		if (!isDisposed(endPageNum)) {
			return readPage(pageNum);
		}
		// no page matches
		throw new PagedFileException(messageOnFail);
	}

	private Page searchPageIncreasing(int startPageNum, int endPageNum, String messageOnFail) {
		return searchPage(startPageNum, endPageNum, (x) -> x + 1, messageOnFail);
	}

	private Page searchPageDecreasing(int startPageNum, int endPageNum, String messageOnFail) {
		return searchPage(startPageNum, endPageNum, (x) -> x - 1, messageOnFail);
	}

	public Page getFirstPage() {
		return searchPageIncreasing(0, N - 1, "no first page");
	}

	public Page getLastPage() {
		return searchPageDecreasing(N - 1, 0, "no last page");
	}

	public Page getPreviousPage(int currentPageNum) {
		return searchPageDecreasing(currentPageNum - 1, 0, "no previous page");
	}

	public Page getNextPage(int currentPageNum) {
		return searchPageIncreasing(currentPageNum + 1, N - 1, "no next page");
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
			throw new PagedFileException(String.format("mark page[%d] as dirty which is not in buffer pool", pageNum));
		}
		Page page = buffer.get(pageNum);
		if (!page.pinned) {
			throw new PagedFileException(String.format("mark an unpinned page[%d] as dirty", pageNum));
		}
		page.dirty = true;
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

	void writeBack(int pageNum) {
		checkState(buffer.contains(pageNum));
		writeBack(buffer.get(pageNum));
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
		if (!buffer.contains(pageNum)) {
			throw new PagedFileException(String.format(
					"force page[%d], which is not in the buffer pool", pageNum));
		}
		writeBack(pageNum);

		Page page = buffer.get(pageNum);
		page.dirty = false;
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
		for (int pageNum : buffer.getAllPages()) {
			forcePage(pageNum);
		}
	}

}
