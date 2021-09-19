package me.nettee.pancake.core.record;

public class RecordFileException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RecordFileException(String msg) {
		super(msg);
	}
	
	public RecordFileException(Throwable reason) {
		super(reason);
	}

}
