package me.nettee.pancake.core.record;

import me.nettee.pancake.core.page.Page;

public class RecordPage {
	
	private final Page page;
	private final int recordSize;

	RecordPage(Page page, int recordSize) {
		this.page = page;
		this.recordSize = recordSize;
		init();
	}
	
	private void init() {
		
	}

}
