package me.nettee.pancake.core.page;

import java.util.Arrays;

public class Pages {

    public static final byte DEFAULT_BYTE = (byte) 0xee;

    public static byte[] makeDefaultBytes(byte value, int length) {
        byte[] data = new byte[length];
        Arrays.fill(data, value);
        return data;
    }

    public static byte[] makeDefaultBytes(int length) {
        return makeDefaultBytes(DEFAULT_BYTE, length);
    }
}
