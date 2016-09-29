package me.nettee.pancake.core.storage;

public class Page {
	
	public static final int PAGE_SIZE = 4092;
	
	private int pageNum;
	private byte[] data;
	
	public Page() {
		data = new byte[PAGE_SIZE];
	}
	
	public int getPageNum() {
		return pageNum;
	}
	
	public byte[] getData() {
		return data;
	}

}
