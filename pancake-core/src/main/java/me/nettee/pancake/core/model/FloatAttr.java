package me.nettee.pancake.core.model;

import java.nio.ByteBuffer;

public class FloatAttr extends Attr {

    private static final int LENGTH = 4;

    private final float value;

    public FloatAttr(float value) {
        this.value = value;
    }

    @Override
    public byte[] getData() {
        return ByteBuffer.allocate(LENGTH).putFloat(value).array();
    }

    @Override
    public int getLength() {
        return LENGTH;
    }
}
