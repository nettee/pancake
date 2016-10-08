package me.nettee.pancake.core.page;

public class Page {
	
	Page(int num) {
		this.num = num;
		pinned = true;
		dirty = true;
		data = new byte[PagedFile.PAGE_SIZE];
	}
	
	int num;
	boolean pinned;
	boolean dirty;
	byte[] data;

}
