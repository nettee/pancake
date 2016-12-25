package me.nettee.pancake.core.page;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;
import java.util.TreeMap;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.log4j.Logger;

/**
 * Paged file is the bottom component of pancake-core. This component provides
 * facilities for higher-level client components to perform file I/O in terms of
 * pages.
 * 
 * Currently no buffer
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
	private PageBuffer buffer2 = new PageBuffer();
	private int nPages;

	@Deprecated
	private Page[] buffer = new Page[BUFFER_SIZE];
	/**
	 * Mapping from pageNum to bufSlot
	 */
	@Deprecated
	private TreeMap<Integer, Integer> bufMap;

	/**
	 * number of pages
	 */
	@Deprecated
	private int N;

	private PagedFile(File file) {
		try {
			this.file = new RandomAccessFile(file, "rw");
		} catch (FileNotFoundException e) {
			throw new PagedFileException(e);
		}
		if (file.length() % Page.PAGE_SIZE != 0) {
			logger.warn("file length is not dividable by " + Page.PAGE_SIZE);
		}
		this.bufMap = new TreeMap<Integer, Integer>();
	}

	/**
	 * Create a paged file. The file should not already exist.
	 * 
	 * @param file
	 *            the file in OS
	 * @return created paged file
	 */
	public static PagedFile create(File file) {
		if (file == null) {
			throw new NullPointerException();
		}
		if (file.exists()) {
			throw new PagedFileException("file already exists: " + file.getAbsolutePath());
		}
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
	public static PagedFile open(File file) throws IOException {
		if (file == null) {
			throw new NullPointerException();
		}
		if (!file.exists()) {
			throw new PagedFileException("file does not exist: " + file.getAbsolutePath());
		}
		PagedFile pagedFile = new PagedFile(file);
		pagedFile.loadPages();
		return pagedFile;
	}

	private void initPages() {
		nPages = 0;
	}

	private void loadPages() throws IOException {
		N = (int) (file.length() / Page.PAGE_SIZE);
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
	 * Read page object from file.
	 * 
	 * @param num
	 *            page number
	 * @return
	 * @throws IOException
	 */
	@Deprecated
	private Page readPage(int num) throws IOException {
		Page page = new Page(num);
		file.seek(num * Page.PAGE_SIZE);
		int pageNum = file.readInt();
		if (pageNum != num) {
			throw new AssertionError();
		}
		file.read(page.data);
		return page;
	}

	@Deprecated
	private boolean emptyBuffer(int bufSlot) throws IOException {
		Page page = buffer[bufSlot];
		if (page == null) {
			return false;
		}
		writePage(page);
		bufMap.remove(page.num);
		return true;
	}

	@Deprecated
	private boolean insertIntoBuffer(Page page) throws IOException {
		Random random = new Random();
		int i = random.nextInt(BUFFER_SIZE);
		emptyBuffer(i);
		buffer[i] = page;
		bufMap.put(page.num, i);
		return true;
	}

	/**
	 * Allocate a new page in the file.
	 * 
	 * @return
	 * @throws IOException
	 */
	public Page allocatePage() {
		int pageNum = nPages++;
		Page page = new Page(pageNum);
		try {
			writePage(page);
		} catch (PagedFileException e) {
			String msg = String.format("fail to allocate page[%d]", pageNum);
			throw new PageAllocationException(msg, e);
		}
		buffer2.pinNewPage(page);
		logger.info(String.format("allocate page[%d]", pageNum));
		return page;
	}

	/**
	 * Remove the page specified by <tt>pageNum</tt>.
	 * 
	 * @param pageNum
	 */
	public void disposePage(int pageNum) {
		try {
			file.seek(pageNum * Page.PAGE_SIZE);
			file.writeInt(DISPOSED_PAGE_NUM);
		} catch (IOException e) {
			String msg = String.format("fail to dispose page[%d]", pageNum);
			throw new PageDisposalException(msg, e);
		}
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

	@Deprecated
	public int getNumOfPages() {
		return N;
	}

	public Page getPage(int pageNum) throws IOException {

		if (pageNum >= N) {
			throw new PagedFileException("page index out of bound");
		}

		if (!bufMap.containsKey(pageNum)) {
			// page not in buffer
			Page page = readPage(pageNum);
			insertIntoBuffer(page);
		}

		return buffer[bufMap.get(pageNum)];
	}

	public Page getFirstPage() throws IOException {
		return getPage(0);
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
	public void forcePage(int pageNum) throws IOException {
		if (!bufMap.containsKey(pageNum)) {
			// The page is not in the buffer pool
			return;
		}
		Page page = buffer[bufMap.get(pageNum)];
		if (page == null) {
			throw new AssertionError();
		}
		writePage(page);
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
		for (int i = 0; i < N; i++) {
			forcePage(i);
		}
	}

}
