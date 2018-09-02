package me.nettee.pancake.core.model;

import java.io.*;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represent the type of an attribute in a record.
 * <p>
 * There are three types: <b>int</b>, <b>float</b>, and <b>string</b>. Type
 * int and float have length 4. The length of string is arbitrary, but no more
 * than {@code MAX_STRING_LEN}.
 */
public class AttrType {

    private static int MAX_STRING_LEN = 256;

    // TODO flyweight pattern

    enum Type {
        INT(1),
        FLOAT(2),
        STRING(3),
        ;

        private final int value;

        Type(int value) {
            this.value = value;
        }

        public int toInt() {
            return value;
        }

        public static Type fromInt(int value) {
            switch (value) {
                case 1: return INT;
                case 2: return FLOAT;
                case 3: return STRING;
                default: throw new AssertionError();
            }
        }
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

    public void writeObject(DataOutputStream os) throws IOException {
        os.writeInt(type.toInt());
        os.writeInt(length);
    }

    public static AttrType readObject(DataInputStream in) throws IOException {
        int typeValue = in.readInt();
        int length = in.readInt();
        return new AttrType(Type.fromInt(typeValue), length);
    }

    @Override
    public String toString() {
        String typeString = type.name();
        if (isInt() || isFloat()) {
            return typeString;
        } else {
            return String.format("%s(%d)", typeString, length);
        }
    }
}
