package me.nettee.pancake.core.page;

import java.util.Arrays;

public class Page {

	public static final int PAGE_SIZE = 4096;
	public static final int DATA_SIZE = 4092;

	int num;
	boolean pinned;
	boolean dirty;
	byte[] data;

	static Page newInstanceByNum(int num) {
		Page page = new Page();
		page.num = num;
		page.pinned = true;
		page.dirty = true;
		page.data = new byte[DATA_SIZE];
		Arrays.fill(page.data, (byte) 0xee);
		return page;
	}
}
