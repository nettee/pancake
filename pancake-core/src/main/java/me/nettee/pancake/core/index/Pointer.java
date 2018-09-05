package me.nettee.pancake.core.index;

import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class Pointer {

    static final int SIZE = 8;
    private static final int POINTER_TAG = -1;

    private final int pageNum;

    private Pointer(int pageNum) {
        this.pageNum = pageNum;
    }

    public static Pointer fromBytes(byte[] data) {
        checkArgument(data.length == SIZE);
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        int pointerTag = byteBuffer.getInt();
        checkState(pointerTag == POINTER_TAG);
        int pageNum = byteBuffer.getInt();
        return new Pointer(pageNum);
    }

    public byte[] toBytes() {
        return ByteBuffer.allocate(SIZE)
                .putInt(POINTER_TAG)
                .putInt(pageNum)
                .array();
    }

    @Override
    public String toString() {
        return String.format("Pointer<%d>", pageNum);
    }
}
