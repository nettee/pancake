package me.nettee.pancake.core.storage;

public abstract class PageContainer {

	abstract public Page getFirstPage();

	abstract public Page getLastPage();

	abstract public Page getPrevPage(int currentPageNum);

	abstract public Page getNextPage(int currentPageNum);

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
