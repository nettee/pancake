package me.nettee.pancake.core.model;

import java.nio.ByteBuffer;

public class IntAttr extends Attr {

    private static final int LENGTH = 4;

    private final int value;

    public IntAttr(int value) {
        this.value = value;
    }

    public static IntAttr fromBytes(byte[] data) {
        throw new UnsupportedOperationException();
    }

    public byte[] getData() {
        return ByteBuffer.allocate(LENGTH).putInt(value).array();
    }

    @Override
    public int getLength() {
        return LENGTH;
    }

    @Override
    public int compareTo(Attr attr) {
        if (!(attr instanceof IntAttr)) {
            throw new ClassCastException();
        }
        IntAttr that = (IntAttr) attr;
        return Integer.compare(this.value, that.value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
