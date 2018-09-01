package me.nettee.pancake.core.model;

import java.nio.ByteBuffer;

public class IntAttr extends Attr {

    private static final int LENGTH = 4;

    private final int value;

    public IntAttr(int value) {
        this.value = value;
    }

    public byte[] getData() {
        return ByteBuffer.allocate(LENGTH).putInt(value).array();
    }

    @Override
    public int getLength() {
        return LENGTH;
    }
}
