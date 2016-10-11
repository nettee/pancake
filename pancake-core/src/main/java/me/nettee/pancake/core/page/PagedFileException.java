package me.nettee.pancake.core.page;

public class PagedFileException extends RuntimeException {
	
	private static final long serialVersionUID = 1L;
	
	public PagedFileException() {
		super();
	}
	
	public PagedFileException(String msg) {
		super(msg);
	}
	
	public PagedFileException(Throwable reason) {
		super(reason);
	}
	
	public PagedFileException(String msg, Throwable reason) {
		super(msg, reason);
	}


}
