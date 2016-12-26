package me.nettee.pancake.core.page;

import java.util.Arrays;

public class Page {

	public static final int PAGE_SIZE = 4096;
	public static final int DATA_SIZE = 4092;

	int num;
	byte[] data;
	
	Page(int num) {
		this.num = num;
		this.data = new byte[DATA_SIZE];
		Arrays.fill(this.data, (byte) 0xee);
	}
	
	public int getNum() {
		return num;
	}

	public byte[] getData() {
		return data;
	}
}
