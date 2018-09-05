package me.nettee.pancake.core.model;

import com.google.common.base.Preconditions;

import java.nio.ByteBuffer;

/**
 * The <tt>RID</tt> class defines unique identifiers for records within a given
 * file.
 * <p>
 * Record identifiers will serve as tuple identifiers for higher-level
 * components. The record identifier will not change if the record is updated,
 * or as the result of an insertion or deletion of a different record.
 * 
 * @author nettee
 *
 */
public class RID implements Comparable<RID> {

    private static final int SIZE = 8;

	public int pageNum;
	public int slotNum;

	public RID(int pageNum, int slotNum) {
		this.pageNum = pageNum;
		this.slotNum = slotNum;
	}

	public static RID fromBytes(byte[] data) {
	    Preconditions.checkArgument(data.length == SIZE);
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        int pageNum = byteBuffer.getInt();
        int slotNum = byteBuffer.getInt();
        return new RID(pageNum, slotNum);
    }

    public byte[] toBytes() {
	    return ByteBuffer.allocate(SIZE).putInt(pageNum).putInt(slotNum).array();
    }

    @Override
    public int compareTo(RID that) {
	    int c = Integer.compare(this.pageNum, that.pageNum);
	    return c != 0 ? c : Integer.compare(this.slotNum, that.slotNum);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RID)) {
            return false;
        }
        RID that = (RID) obj;
        return this.compareTo(that) == 0;
    }

    @Override
	public String toString() {
		return String.format("<%d,%d>", pageNum, slotNum);
	}

}
