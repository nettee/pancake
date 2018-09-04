package me.nettee.pancake.core.index;

import me.nettee.pancake.core.page.Page;

public class NonLeafIndexNode extends IndexNode {

    public NonLeafIndexNode(Page page, IndexHeader indexHeader) {
        super(page, indexHeader);
    }

    public static NonLeafIndexNode open(Page page, IndexHeader indexHeader, Header pageHeader) {
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
