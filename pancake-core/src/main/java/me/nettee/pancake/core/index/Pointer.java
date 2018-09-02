package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.RID;

import java.nio.ByteBuffer;

public class Pointer {

    public static final int SIZE = 8;

    private enum Type {
        RID,
    }

    private Type type;
    private RID rid;

    private Pointer() {
    }

    public static Pointer fromRid(RID rid) {
        Pointer pointer = new Pointer();
        pointer.type = Type.RID;
        pointer.rid = rid;
        return pointer;
    }

    public byte[] getData() {
        if (type.equals(Type.RID)) {
            return ByteBuffer.allocate(SIZE)
                    .putInt(rid.pageNum)
                    .putInt(rid.slotNum)
                    .array();
        } else {
            throw new AssertionError();
        }
    }

    @Override
    public String toString() {
        if (type.equals(Type.RID)) {
            return rid.toString();
        } else {
            return super.toString();
        }
    }
}
