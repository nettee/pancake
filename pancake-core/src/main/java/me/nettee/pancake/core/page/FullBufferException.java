package me.nettee.pancake.core.page;

public class FullBufferException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public FullBufferException() {
		super();
	}

	public FullBufferException(String msg) {
		super(msg);
	}

	public FullBufferException(Throwable reason) {
		super(reason);
	}

	public FullBufferException(String msg, Throwable reason) {
		super(msg, reason);
	}


}
