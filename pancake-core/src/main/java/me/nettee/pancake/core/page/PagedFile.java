package me.nettee.pancake.core.page;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
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

	// private static int pageNumCount = 31;

	private RandomAccessFile file;
	private TreeMap<Integer, Page> buffer;

	private PagedFile(File file) {
		try {
			this.file = new RandomAccessFile(file, "rw");
		} catch (FileNotFoundException e) {
			throw new PagedFileException(e);
		}
		if (file.length() % 4096 != 0) {
			logger.warn("file length is not dividable by 4096");
		}
		this.buffer = new TreeMap<Integer, Page>();
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

	public static PagedFile open(File file) throws FileNotFoundException {
		if (file == null) {
			throw new NullPointerException();
		}
		PagedFile pagedFile = new PagedFile(file);
		pagedFile.loadPages();
		return pagedFile;
	}

	private void initPages() {
		// TODO
	}

	private void loadPages() {
		// TODO
	}

	public void close() {
		try {
			file.close();
		} catch (IOException e) {
			throw new PagedFileException(e);
		}
	}

	private void writePageToSlot(int slot, Page page) throws IOException {
		file.seek(slot * Page.PAGE_SIZE);
		file.writeInt(page.num);
		file.write(page.data);
		if (file.getFilePointer() != (slot + 1) * Page.PAGE_SIZE) {
			throw new AssertionError();
		}
	}

	public Page allocatePage() throws IOException {

		int N = getNumOfPages();

		Page page = Page.newInstanceByNum(N);

		writePageToSlot(N, page);

		buffer.put(page.num, page);
		logger.debug("buffer size: " + buffer.size());

		return page;
	}

	public int getNumOfPages() throws IOException {
		return (int) (file.length() / 4096);
	}

	public Page getPage(int N) throws IOException {

		if (!buffer.containsKey(N)) {
			file.seek(N * Page.PAGE_SIZE);
			int pageNum = file.readInt();
			Page page = Page.newInstanceByNum(pageNum);
			file.read(page.data);
			buffer.put(page.num, page);
		}

		return buffer.get(N);
	}

	public Page getFirstPage() throws IOException {
		return getPage(0);
	}

	public void forcePage(int pageNum) throws IOException {
		Logger logger = Logger.getLogger(this.getClass());
		logger.debug("force: pageNum = " + pageNum);
		// logger.debug("pageNum: " + pageNum);
		// logger.debug("buffer size: " + buffer.size());
		// for (int key : buffer.keySet()) {
		// logger.debug("key: " + key);
		// }
		Page page = buffer.get(pageNum);
		if (page == null) {
			throw new AssertionError();
		}
		writePageToSlot(page.num, page);
	}

}
