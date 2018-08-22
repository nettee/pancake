package me.nettee.pancake.core.record;

public class RecordNotExistException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RecordNotExistException(String msg) {
		super(msg);
	}

}
