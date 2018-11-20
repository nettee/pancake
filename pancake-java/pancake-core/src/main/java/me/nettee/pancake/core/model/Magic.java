package me.nettee.pancake.core.model;

import me.nettee.pancake.core.index.IndexException;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Magic {

    private final String text;

    public Magic(String text) {
        this.text = text;
    }

    public byte[] getBytes() {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    public void check(DataInputStream in) throws IOException {
        byte[] magic0 = new byte[text.length()];
        int read = in.read(magic0);
        if (read != text.length()) {
            throw new IndexException("Magic not match");
        }
        if (!text.equals(new String(magic0, StandardCharsets.US_ASCII))) {
            throw new IndexException("Magic not match");
        }
    }
}