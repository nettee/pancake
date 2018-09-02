package me.nettee.pancake.core.index;

import com.google.common.base.Preconditions;
import me.nettee.pancake.core.model.RID;

import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkArgument;

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

    public static Pointer fromBytes(byte[] data) {
        checkArgument(data.length == SIZE);
        // Only support rid type now.
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        int pageNum = byteBuffer.getInt();
        int slotNum = byteBuffer.getInt();
        return Pointer.fromRid(new RID(pageNum, slotNum));
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
