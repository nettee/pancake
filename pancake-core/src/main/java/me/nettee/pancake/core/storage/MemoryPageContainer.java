package me.nettee.pancake.core.storage;

import java.util.NoSuchElementException;

public class MemoryPageContainer extends PageContainer {

	@Override
	public Page getFirstPage() {
		throw new NoSuchElementException();
	}

	@Override
	public Page getLastPage() {
		throw new NoSuchElementException();
	}

	@Override
	public Page getPrevPage(int currentPageNum) {
		throw new NoSuchElementException();
	}

	@Override
	public Page getNextPage(int currentPageNum) {
		throw new NoSuchElementException();
	}

	@Override
	public Page getThisPage(int pageNum) {
		throw new NoSuchElementException();
	}

	@Override
	public Page allocatePage() {
		Page page = new Page();
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
