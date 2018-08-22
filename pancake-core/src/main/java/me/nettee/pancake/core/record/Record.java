package me.nettee.pancake.core.record;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Record {

    private static final Charset CHARSET = StandardCharsets.US_ASCII;

    private byte[] data;

    public Record(byte[] data) {
        this.data = data;
    }

    static Record fromString(String str) {
        return new Record(str.getBytes(CHARSET));
    }

    public byte[] getData() {
        return data;
    }

    public int getLength() {
        return data.length;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Record)) {
            return false;
        }
        Record that = (Record) obj;
        if (this.getLength() != that.getLength()) {
            return false;
        }
        for (int i = 0; i < getLength(); i++) {
            if (this.data[i] != that.data[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return new String(data, CHARSET);
    }
}
