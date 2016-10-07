package me.nettee.pancake.core.storage;

import java.util.ArrayList;
import java.util.NoSuchElementException;

public abstract class PageContainer {
	
	protected ArrayList<Page> pages = new ArrayList<Page>();

	public PageContainer() {
	}

	public Page getFirstPage() {
		if (pages.isEmpty()) {
			throw new NoSuchElementException();
		} else {
			return pages.get(0);
		}
	}
	
	public Page getLastPage() {
		if (pages.isEmpty()) {
			throw new NoSuchElementException();
		} else {
			return pages.get(pages.size() - 1);
		}
	}

	public Page getPrevPage(int currentPageNum) {
		int N = pages.size();
		for (int i = 0; i < N; i++) {
			Page page = pages.get(i);
			if (page.getPageNum() == currentPageNum) {
				if (i == 0) {
					throw new NoSuchElementException();
				} else {
					return pages.get(i - 1);
				}
			}
		}
		throw new NoSuchElementException();
	}
	
	public Page getNextPage(int currentPageNum) {
		int N = pages.size();
		for (int i = 0; i < N; i++) {
			Page page = pages.get(i);
			if (page.getPageNum() == currentPageNum) {
				if (i == N - 1) {
					throw new NoSuchElementException();
				} else {
					return pages.get(i + 1);
				}
			}
		}
		throw new NoSuchElementException();
	}

	public Page getPrevPage(Page current) {
		return getPrevPage(current.getPageNum());
	}

	public Page getNextPage(Page current) {
		return getNextPage(current.getPageNum());
	}

	abstract public Page getThisPage(int pageNum);

	abstract public Page allocatePage();

	abstract public void disposePage(int pageNum);

	public void disposePage(Page page) {
		disposePage(page.getPageNum());
	}

	abstract public void markDirty(int pageNum);

	public void markDirty(Page page) {
		markDirty(page.getPageNum());
	}

}
