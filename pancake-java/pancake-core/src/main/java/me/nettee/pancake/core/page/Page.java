package me.nettee.pancake.core.page;

/**
 * The size of a page is 4096 bytes, The first 4 bytes represents page number (integer),
 * and the rest 4092 bytes stores data.
 */
public class Page {

    public static final int PAGE_SIZE = 4096;
    public static final int DATA_SIZE = 4092;

    int num;
    boolean pinned = false;
    boolean dirty = false;
    byte[] data;

    Page(int num) {
        this.num = num;
        // Fill the page with default bytes for ease of debugging.
        this.data = Pages.makeDefaultBytes(DATA_SIZE);
    }

    public int getNum() {
        return num;
    }

    public byte[] getData() {
        return data;
    }
}
