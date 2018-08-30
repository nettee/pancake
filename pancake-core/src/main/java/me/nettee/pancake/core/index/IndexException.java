package me.nettee.pancake.core.index;

public class IndexException extends RuntimeException {

    public IndexException(String msg) {
        super(msg);
    }

    public IndexException(Throwable reason) {
        super(reason);
    }
}
