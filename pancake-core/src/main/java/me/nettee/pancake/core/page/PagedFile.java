package me.nettee.pancake.core.page;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.apache.log4j.Logger;

/**
 * Currently no buffer
 * @author william
 *
 */
public class PagedFile {
	
	private static Logger logger = Logger.getLogger(PagedFile.class);
	
//	private static int pageNumCount = 31;
	
	private RandomAccessFile file;
//	private TreeMap<Integer, Page>buffer;
	
	private PagedFile(File file) throws FileNotFoundException {
		if (file == null) {
			throw new NullPointerException();
		}
		this.file = new RandomAccessFile(file, "rw");
		if (file.length() % 4096 != 0) {
			logger.warn("file length is not dividable by 4096");
		}
//		this.buffer = new TreeMap<Integer, Page>();
	}
	
	public static PagedFile open(File file) throws FileNotFoundException {
		return new PagedFile(file);
	}
	
	public void close() throws IOException {
		file.close();
	}
	
	public Page allocatePage() throws IOException {
		
		int N = getPageNum();
		
		Page page = Page.newInstanceByNum(N);
		
		file.seek(N * Page.PAGE_SIZE);
		file.writeInt(page.num);
		file.write(page.data);
		if (file.getFilePointer() != (N + 1) * Page.PAGE_SIZE) {
			throw new AssertionError();
		}
		
		return page;
	}
	
	public int getPageNum() throws IOException {
		return (int) (file.length() / 4096);
	}
	
	public Page getPage(int N) throws IOException {
		file.seek(N * Page.PAGE_SIZE);
		int pageNum = file.readInt();
		Page page = Page.newInstanceByNum(pageNum);
		file.read(page.data);
		return page;
	}
	
	public Page getFirstPage() throws IOException {
		return getPage(0);
	}

}
