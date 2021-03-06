package me.nettee.pancake.core.model;

import java.nio.ByteBuffer;

public class FloatAttr extends Attr {

    private static final int LENGTH = 4;

    private final float value;

    public FloatAttr(float value) {
        this.value = value;
    }

    public static FloatAttr fromBytes(byte[] data) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] toBytes() {
        return ByteBuffer.allocate(LENGTH).putFloat(value).array();
    }

    @Override
    public int getLength() {
        return LENGTH;
    }

    @Override
    public int compareTo(Attr attr) {
        if (!(attr instanceof FloatAttr)) {
            throw new ClassCastException();
        }
        FloatAttr that = (FloatAttr) attr;
        return Float.compare(this.value, that.value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
