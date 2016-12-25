package me.nettee.pancake.core.page;

public class PageDisposalException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PageDisposalException() {
		super();
	}

	public PageDisposalException(String msg) {
		super(msg);
	}

	public PageDisposalException(Throwable reason) {
		super(reason);
	}

	public PageDisposalException(String msg, Throwable reason) {
		super(msg, reason);
	}

}
