package me.nettee.pancake.core.page;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;
import java.util.TreeMap;

import org.apache.log4j.Logger;

/**
 * Currently no buffer
 * 
 * @author william
 *
 */
public class PagedFile {

	private static Logger logger = Logger.getLogger(PagedFile.class);

	private RandomAccessFile file;

	public static final int BUFFER_SIZE = 4;
	private Page[] buffer = new Page[BUFFER_SIZE];
	/**
	 * Mapping from pageNum to bufSlot
	 */
	private TreeMap<Integer, Integer> bufMap;

	/**
	 * number of pages
	 */
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
		N = 0;
	}

	private void loadPages() throws IOException {
		N = (int) (file.length() / Page.PAGE_SIZE);
	}

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

	/**
	 * Write page object to file
	 * 
	 * @param page
	 * @throws IOException
	 */
	private void writePage(Page page) throws IOException {
		int num = page.num;
		file.seek(num * Page.PAGE_SIZE);
		file.writeInt(page.num);
		file.write(page.data);
		if (file.getFilePointer() != (num + 1) * Page.PAGE_SIZE) {
			throw new AssertionError();
		}
	}

	private boolean emptyBuffer(int bufSlot) throws IOException {
		Page page = buffer[bufSlot];
		if (page == null) {
			return false;
		}
		writePage(page);
		bufMap.remove(page.num);
		return true;
	}

	private boolean insertIntoBuffer(Page page) throws IOException {
		Random random = new Random();
		int i = random.nextInt(BUFFER_SIZE);
		emptyBuffer(i);
		buffer[i] = page;
		bufMap.put(page.num, i);
		return true;
	}

	public Page allocatePage() throws IOException {
		Page page = new Page(N);
		N++;
		insertIntoBuffer(page);
		return page;
	}

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
