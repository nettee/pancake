package me.nettee.pancake.core.storage;

public class Page {
	
	public static final int PAGE_SIZE = 4092; // 4096 - 4
	
	private static int pageNumCount = 1000;
	
	private int pageNum;
	private byte[] data;
	
	public Page() {
		pageNum = pageNumCount;
		pageNumCount++;
		data = new byte[PAGE_SIZE];
	}
	
	public int getPageNum() {
		return pageNum;
	}
	
	public byte[] getData() {
		return data;
	}

}
