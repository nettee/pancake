package me.nettee.pancake.core.index;

import me.nettee.pancake.core.page.Page;

import static com.google.common.base.Preconditions.checkArgument;

public class NonLeafIndexNode extends IndexNode {

    public NonLeafIndexNode(Page page, IndexHeader indexHeader) {
        super(page, indexHeader);
    }

    public static NonLeafIndexNode open(Page page, IndexHeader indexHeader, Header pageHeader) {
        checkArgument(!pageHeader.isLeaf);
        throw new UnsupportedOperationException();
    }

    @Override
    boolean isLeaf() {
        return false;
    }

    @Override
    boolean isFull() {
        return pageHeader.N >= indexHeader.branchingFactor;
    }

    @Override
    void writeToPage() {
        throw new UnsupportedOperationException();
    }

    @Override
    String dump() {
        return "\n";
    }
}
