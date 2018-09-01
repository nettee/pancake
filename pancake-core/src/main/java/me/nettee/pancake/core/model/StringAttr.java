package me.nettee.pancake.core.model;

import java.nio.charset.StandardCharsets;

public class StringAttr extends Attr {

    private final String value;

    public StringAttr(String value) {
        this.value = value;
    }

    @Override
    public byte[] getData() {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public int getLength() {
        return value.length();
    }
}
