package me.nettee.pancake.core.page;

public class PageAllocationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PageAllocationException() {
		super();
	}

	public PageAllocationException(String msg) {
		super(msg);
	}

	public PageAllocationException(Throwable reason) {
		super(reason);
	}

	public PageAllocationException(String msg, Throwable reason) {
		super(msg, reason);
	}

}
