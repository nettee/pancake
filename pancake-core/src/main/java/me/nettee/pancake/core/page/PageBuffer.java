package me.nettee.pancake.core.page;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import com.google.common.base.Optional;

public class PageBuffer {
	
	private static Logger logger = Logger.getLogger(PageBuffer.class);
	
	private static class ArgmentedPage {
		
		final Page page;
		boolean pinned = true;
		boolean dirty = false;
		
		ArgmentedPage(Page page) {
			this.page = page;
		}
	}
	
	private Map<Integer, ArgmentedPage> map = new TreeMap<>();
	
	public void pinNewPage(Page page) {
		checkNotNull(page);
		map.put(page.num, new ArgmentedPage(page));
		logger.info(String.format("pin new page[%d]", page.num));
	}
	
	public Optional<Page> getPage(int pageNum) {
		if (map.containsKey(pageNum)) {
			Page page = map.get(pageNum).page;
			return Optional.of(page);
		} else {
			return Optional.absent();
		}
	}
	
	private void checkExistence(int pageNum) {
		if (!map.containsKey(pageNum)) {
			throw new IllegalArgumentException();
		} 
	}
	
	public boolean isPinned(int pageNum) {
		checkExistence(pageNum);
		return map.get(pageNum).pinned;
	}
	
	public void unpin(int pageNum) {
		checkExistence(pageNum);
		map.get(pageNum).pinned = false;
		logger.info(String.format("unpin page[%d]", pageNum));
	}
	
	public boolean isDirty(int pageNum) {
		checkExistence(pageNum);
		return map.get(pageNum).dirty;
	}

}
