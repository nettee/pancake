package me.nettee.pancake.core.index;

import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * The size of {@code NodePointer} is set to 8, the same as {@code RID},
 * so that the leaf node and non-leaf node in B+ tree can have the same
 * branching factor.
 */
public class NodePointer {

    static final int SIZE = 8;
    private static final int POINTER_TAG = -1;

    private final int pageNum;

    NodePointer(int pageNum) {
        this.pageNum = pageNum;
    }

    public static NodePointer fromBytes(byte[] data) {
        checkArgument(data.length == SIZE);
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        int pointerTag = byteBuffer.getInt();
        checkState(pointerTag == POINTER_TAG);
        int pageNum = byteBuffer.getInt();
        return new NodePointer(pageNum);
    }

    public int getPageNum() {
        return pageNum;
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
