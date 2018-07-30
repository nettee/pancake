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
public class RID {

	public int pageNum;
	public int slotNum;

	public RID(int pageNum, int slotNum) {
		this.pageNum = pageNum;
		this.slotNum = slotNum;
	}

	@Override
	public String toString() {
		return String.format("<%d,%d>", pageNum, slotNum);
	}
}
