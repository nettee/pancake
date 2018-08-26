package me.nettee.pancake.core.record;

public class Attr {

    private final byte[] data;

    public Attr(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    public int getLength() {
        return data.length;
    }
}