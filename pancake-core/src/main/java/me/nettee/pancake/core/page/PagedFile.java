package me.nettee.pancake.core.page;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Deque;
import java.util.LinkedList;
import java.util.function.UnaryOperator;

import org.apache.log4j.Logger;

import com.google.common.base.Optional;

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

	private static Logger logger = Logger.getLogger(PagedFile.class);

	public static final int BUFFER_SIZE = 4;

	/*
	 * Once a page is disposed, it's page number (the first 4 bytes) will be
	 * changed to this value. Thus, disposed pages can be detected when the
	 * paged file is reopened.
	 */
	private static final int DISPOSED_PAGE_NUM = -1;

	private RandomAccessFile file;
	private PageBuffer buffer = new PageBuffer();
	private int nPages;
	private Deque<Integer> disposedPageNums = new LinkedList<>();

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
		nPages = 0;
	}

	private void loadPages() throws IOException {
		if (file.length() % Page.PAGE_SIZE != 0) {
			logger.warn("file length is not dividable by " + Page.PAGE_SIZE);
		}
		nPages = (int) (file.length() / Page.PAGE_SIZE);
		// TODO get disposed pages
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

	/**
	 * Allocate a new page in the file.
	 * 
	 * @return
	 * @throws IOException
	 */
	public Page allocatePage() {
		int pageNum;
		if (disposedPageNums.isEmpty()) {
			pageNum = nPages++;
		} else {
			pageNum = disposedPageNums.pop();
		}
		Page page = new Page(pageNum);
		try {
			writePage(page);
		} catch (PagedFileException e) {
			String msg = String.format("fail to allocate page[%d]", pageNum);
			throw new PagedFileException(msg, e);
		}
		buffer.pinNewPage(page);
		logger.info(String.format("allocate page[%d]", pageNum));
		return page;
	}

	/**
	 * Remove the page specified by <tt>pageNum</tt>.
	 * 
	 * @param pageNum
	 */
	public void disposePage(int pageNum) {
		getPage(pageNum); // XXX check whether this page exists
		if (buffer.isPinned(pageNum)) {
			String msg = String.format("fail to dispose unpinned page[%d]", pageNum);
			throw new PagedFileException(msg);
		}
		
		try {
			file.seek(pageNum * Page.PAGE_SIZE);
			file.writeInt(DISPOSED_PAGE_NUM);
		} catch (IOException e) {
			String msg = String.format("fail to dispose page[%d]", pageNum);
			throw new PagedFileException(msg, e);
		}
		disposedPageNums.push(pageNum);
		logger.info(String.format("dispose page[%d]", pageNum));
	}

	private void writePage(Page page) {
		int startPointer = page.num * Page.PAGE_SIZE;
		int endPointer = (page.num + 1) * Page.PAGE_SIZE;
		String msg = String.format("fail to write page[%d]", page.num);
		try {
			file.seek(startPointer);
			file.writeInt(page.num);
			file.write(page.data);
			if (file.getFilePointer() != endPointer) {
				throw new PagedFileException(msg);
			}
		} catch (IOException e) {
			throw new PagedFileException(msg);
		}
	}

	public int getNumOfPages() {
		return nPages;
	}

	private void checkPageNumRange(int pageNum) {
		if (pageNum >= nPages) {
			throw new PagedFileException("page index out of bound: " + pageNum);
		}
	}

	private boolean isDisposed(int pageNum) throws IOException {
		file.seek(pageNum * Page.PAGE_SIZE);
		int actualNum = file.readInt();
		return actualNum == DISPOSED_PAGE_NUM;
	}

	private Page readPage(int pageNum) throws IOException {
		Page page = new Page(pageNum);
		file.seek(pageNum * Page.PAGE_SIZE);
		file.readInt();
		file.read(page.data);
		return page;
	}
	
	public Page getPage(int pageNum) {
		// FIXME read buffer first
		checkPageNumRange(pageNum);
		try {
			if (isDisposed(pageNum)) {
				String msg = String.format("page[%d] is disposed", pageNum);
				throw new PagedFileException(msg);
			}
			return readPage(pageNum);
		} catch (IOException e) {
			throw new PagedFileException(e);
		}
	}

	private Page searchPage(int startPageNum, int endPageNum, UnaryOperator<Integer> next, String messageOnFail) {
		int pageNum = startPageNum;
		try {
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
		} catch (IOException e) {
			throw new PagedFileException(e);
		}
	}

	public Page getFirstPage() {
		return searchPage(0, nPages - 1, addOne, "no page in the file");
	}

	public Page getLastPage() {
		return searchPage(nPages - 1, 0, subOne, "no page in the file");
	}

	public Page getPreviousPage(int currentPageNum) {
		return searchPage(currentPageNum - 1, 0, subOne, "no previous page");
	}

	public Page getNextPage(int currentPageNum) {
		return searchPage(currentPageNum + 1, nPages - 1, addOne, "no next page");
	}
	
	public void unpinPage(int pageNum) {
		buffer.unpin(pageNum);
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
		Optional<Page> optional = buffer.getPage(pageNum);
		if (!optional.isPresent()) {
			return;
		}
		Page page = optional.get();
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
	 * @see forcePage
	 * @throws IOException
	 */
	public void forceAllPages() throws IOException {
		for (int i = 0; i < nPages; i++) {
			forcePage(i);
		}
	}

}
