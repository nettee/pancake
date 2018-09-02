package me.nettee.pancake.core.model;

import java.nio.charset.StandardCharsets;

public class StringAttr extends Attr {

    private final String value;

    public StringAttr(String value) {
        this.value = value;
    }

    public static StringAttr fromBytes(byte[] data) {
        String value = new String(data, StandardCharsets.US_ASCII);
        return new StringAttr(value);
    }

    @Override
    public byte[] getData() {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public int getLength() {
        return value.length();
    }

    @Override
    public int compareTo(Attr attr) {
        if (!(attr instanceof StringAttr)) {
            throw new ClassCastException();
        }
        StringAttr that = (StringAttr) attr;
        return this.value.compareTo(that.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
