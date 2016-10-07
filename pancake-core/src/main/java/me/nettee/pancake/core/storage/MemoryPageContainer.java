package me.nettee.pancake.core.storage;

import java.util.NoSuchElementException;

public class MemoryPageContainer extends PageContainer {

	@Override
	public Page getThisPage(int pageNum) {
		throw new NoSuchElementException();
	}

	@Override
	public Page allocatePage() {
		Page page = new Page();
		pages.add(page);
		return page;
	}

	@Override
	public void disposePage(int pageNum) {
		throw new NoSuchElementException();
	}

	@Override
	public void markDirty(int pageNum) {
		throw new NoSuchElementException();
	}

}
