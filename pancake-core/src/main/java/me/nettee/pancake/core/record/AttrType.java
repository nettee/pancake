package me.nettee.pancake.core.record;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represent the type of an attribute in a record.
 * <p>
 * There are three types: <b>int</b>, <b>float</b>, and <b>string</b>. Type
 * int and float have length 4. The length of string is arbitrary, but no more
 * than {@code MAX_STRING_LEN}.
 */
public class AttrType {

    public static int MAX_STRING_LEN = 256;

    enum Type {
        INT,
        FLOAT,
        STRING,
    }

    public static AttrType INT = new AttrType(Type.INT, 4);
    public static AttrType FLOAT = new AttrType(Type.FLOAT, 4);

    private Type type;
    private int length;

    private AttrType(Type type, int length) {
        this.type = type;
        this.length = length;
    }

    public static AttrType string(int length) {
        checkArgument(1 <= length && length <= MAX_STRING_LEN);
        return new AttrType(Type.STRING, length);
    }

    public boolean isInt() {
        return type.equals(Type.INT);
    }

    public boolean isFloat() {
        return type.equals(Type.FLOAT);
    }

    public boolean isString() {
        return type.equals(Type.STRING);
    }

    public int getLength() {
        return length;
    }
}
