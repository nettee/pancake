package me.nettee.pancake.core.record;

public class RecordNotExistException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RecordNotExistException() {
		super();
	}

	public RecordNotExistException(String msg) {
		super(msg);
	}

	public RecordNotExistException(Throwable reason) {
		super(reason);
	}

	public RecordNotExistException(String msg, Throwable reason) {
		super(msg, reason);
	}

}
