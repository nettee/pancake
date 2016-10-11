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

	private RandomAccessFile file;
	private TreeMap<Integer, Page> buffer;

	private PagedFile(File file) {
		try {
			this.file = new RandomAccessFile(file, "rw");
		} catch (FileNotFoundException e) {
			throw new PagedFileException(e);
		}
		if (file.length() % Page.PAGE_SIZE != 0) {
			logger.warn("file length is not dividable by " + Page.PAGE_SIZE);
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

	private void writePage(Page page) throws IOException {
		int slot = page.num;
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

		writePage(page);

		buffer.put(page.num, page);
		logger.debug("buffer size: " + buffer.size());

		return page;
	}

	public int getNumOfPages() throws IOException {
		return (int) (file.length() / 4096);
	}

	public Page getPage(int N) throws IOException {
		
		if (N >= getNumOfPages()) {
			throw new PagedFileException("page index out of bound");
		}

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
		Page page = buffer.get(pageNum);
		if (page == null) {
			throw new AssertionError();
		}
		writePage(page);
	}
	
	public void forceAllPages() throws IOException {
		int N = getNumOfPages();
		for (int i = 0; i < N; i++) {
			forcePage(i);
		}
	}

}
