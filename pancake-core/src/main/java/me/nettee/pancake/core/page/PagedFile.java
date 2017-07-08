package me.nettee.pancake.core.page;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Currently, no buffer implementation.
 * 
 * @author nettee
 *
 */
public class PagedFile {

	private static Logger logger = LoggerFactory.getLogger(PagedFile.class);

	// TODO limit buffer size
	private static final int BUFFER_SIZE = 4;

	private RandomAccessFile file;
	private int N;
//	private Map<Integer, Integer> disposedPageIndexes = new HashMap<>();
	private Deque<Integer> disposedPageNumsStack = new LinkedList<>();

	private Map<Integer, Page> buffer = new TreeMap<>();

	private static final UnaryOperator<Integer> addOne = (x) -> x + 1;
	private static final UnaryOperator<Integer> subOne = (x) -> x - 1;

	private PagedFile(File file) {
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
	 * @throws IOException
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

		// Collect disposed pages and push their nums orderly into the stack.
		int[] disposedPageNums = new int[N+1];
		for (int __ = 0; __ <= N; __++) {
			disposedPageNums[__] = -1;
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
		try {
			file.close();
		} catch (IOException e) {
			throw new PagedFileException(e);
		}
	}

	public int getNumOfPages() {
		return N;
	}

	// TODO Check if this page is disposed, more than just check page index range.
	private void checkPageNumRange(int pageNum) {
		if (pageNum >= N) {
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

	private void writePageToFile(Page page) throws IOException {
		int startPointer = page.num * Page.PAGE_SIZE;
		int endPointer = (page.num + 1) * Page.PAGE_SIZE;
		file.seek(startPointer);
		file.writeInt(page.num);
		file.write(page.data);
		String msg = String.format("fail to write page[%d]", page.num);
		if (file.getFilePointer() != endPointer) {
			throw new PagedFileException(msg);
		}
	}

	/**
	 * Allocate a new page in the file.
	 * 
	 * @return
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
		buffer.put(page.num, page);
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
			// page already disposed
			String msg = String.format("page[%d] already disposed", pageNum);
			throw new PagedFileException(msg);
		}
		if (buffer.containsKey(pageNum)) {
			// page in buffer
			Page page = buffer.get(pageNum);
			if (page.pinned) {
				String msg = String.format("fail to dispose pinned page[%d]", pageNum);
				throw new PagedFileException(msg);
			}
		}
		// TODO fill the page with default byte
		try {
			file.seek(pageNum * Page.PAGE_SIZE);
			file.writeInt(-1 - disposedPageNumsStack.size());
		} catch (IOException e) {
			String msg = String.format("fail to dispose page[%d]", pageNum);
			throw new PagedFileException(msg, e);
		}
		disposedPageNumsStack.push(pageNum);
		logger.debug(String.format("dispose page[%d]", pageNum));
	}

	/*
	 * Read page from buffer or from file.
	 * 
	 * NOTE: This method does not check page number range or disposed page.
	 * 
	 * If the page is in buffer, return it simply. Otherwise, read the page from
	 * file and put it into buffer.
	 */
	private Page readPage(int pageNum) {
		try {
			if (buffer.containsKey(pageNum)) {
				return buffer.get(pageNum);
			} else {
				Page page = readPageFromFile(pageNum);
				buffer.put(page.num, page);
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
			String msg = String.format("page[%d] is disposed", pageNum);
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

	public Page getFirstPage() {
		return searchPage(0, N - 1, addOne, "no first page");
	}

	public Page getLastPage() {
		return searchPage(N - 1, 0, subOne, "no last page");
	}

	public Page getPreviousPage(int currentPageNum) {
		return searchPage(currentPageNum - 1, 0, subOne, "no previous page");
	}

	public Page getNextPage(int currentPageNum) {
		return searchPage(currentPageNum + 1, N - 1, addOne, "no next page");
	}

	public void markDirty(int pageNum) {
		if (buffer.containsKey(pageNum)) {
			Page page = buffer.get(pageNum);
			page.dirty = true;
		}
	}
	
	public void markDirty(Page page) {
		markDirty(page.num);
	}

	public void unpinPage(int pageNum) {
		if (buffer.containsKey(pageNum)) {
			Page page = buffer.get(pageNum);
			page.pinned = false;
		}
	}
	
	public void unpinPage(Page page) {
		unpinPage(page.num);
	}

	/**
	 * This method copies the contents of the page specified by <tt>pageNum</tt>
	 * from the buffer pool to disk if the page is in the buffer pool and is
	 * marked as dirty. The page remains in the buffer pool but is no longer
	 * marked as dirty.
	 * 
	 * @param pageNum
	 * @throws IOException
	 */
	public void forcePage(int pageNum) {
		if (!buffer.containsKey(pageNum)) {
			return;
		}
		Page page = buffer.get(pageNum);
		if (!page.dirty) {
			return;
		}
		try {
			file.seek(pageNum * Page.PAGE_SIZE);
			file.writeInt(pageNum);
			file.write(page.data);
		} catch (IOException e) {
			throw new PagedFileException(e);
		}
	}

	/**
	 * This method copies the contents of all the pages in the buffer pool to
	 * disk. The pages remains in the buffer pool but is no longer marked as
	 * dirty. Calling this method has the same effect as calling
	 * <tt>forcePage</tt> on each page.
	 * 
	 * @throws IOException
	 */
	public void forceAllPages() {
		for (int i = 0; i < N; i++) {
			forcePage(i);
		}
	}

}
