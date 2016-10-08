package me.nettee.pancake.core.page;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.TreeMap;

public class PagedFile {
	
	public static final int PAGE_SIZE = 4092;
	
	private RandomAccessFile file;
	private TreeMap<Integer, Page>buffer;
	
	private PagedFile(File file) throws FileNotFoundException {
		this.file = new RandomAccessFile(file, "rw");
		this.buffer = new TreeMap<Integer, Page>();
	}
	
	public static PagedFile open(File file) throws FileNotFoundException {
		return new PagedFile(file);
	}
	
	public void close() throws IOException {
		file.close();
	}
	
	public Page allocatePage() throws IOException {
		Page page = new Page(31);
		
		byte[] b = new byte[4096];
		Arrays.fill(b, (byte) 0xee);
		byte[] i = ByteBuffer.allocate(4)
				.order(ByteOrder.BIG_ENDIAN)
				.putInt(page.num)
				.array();
		System.arraycopy(i, 0, b, 0, 4);
		file.write(b, 0, 4096);
		
		if (buffer.containsKey(page.num)) {
			throw new IllegalStateException();
		}
		buffer.put(page.num, page);
		return page;
	}

}
