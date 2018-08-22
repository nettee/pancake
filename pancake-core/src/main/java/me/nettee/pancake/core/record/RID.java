package me.nettee.pancake.core.record;

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

	public int pageNum;
	public int slotNum;

	RID(int pageNum, int slotNum) {
		this.pageNum = pageNum;
		this.slotNum = slotNum;
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
