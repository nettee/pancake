package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.page.Page;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class NonLeafIndexNode extends IndexNode {

    private List<Attr> keys;
    private List<NodePointer> pointers;

    private NonLeafIndexNode(Page page, IndexHeader indexHeader) {
        super(page, indexHeader);
        keys = new ArrayList<>(indexHeader.branchingFactor - 1);
        pointers = new ArrayList<>(indexHeader.branchingFactor);
    }

    private NonLeafIndexNode(Page page, IndexHeader indexHeader, Header pageHeader) {
        super(page, indexHeader, pageHeader);
        keys = new ArrayList<>(indexHeader.branchingFactor - 1);
        pointers = new ArrayList<>(indexHeader.branchingFactor);
    }

    public static NonLeafIndexNode create(Page page,
                                       IndexHeader indexHeader,
                                       boolean isRoot) {
        NonLeafIndexNode node = new NonLeafIndexNode(page, indexHeader);
        node.init(isRoot, false);
        return node;
    }

    public static NonLeafIndexNode open(Page page, IndexHeader indexHeader, Header pageHeader) {
        checkArgument(!pageHeader.isLeaf);
        NonLeafIndexNode node = new NonLeafIndexNode(page, indexHeader, pageHeader);
        node.load();
        return node;
    }

    private void load() {
        readFromPage();
    }

    void addTwoChildren(Attr key, int leftNode, int rightNode) {
        checkState(isEmpty());
        keys.add(key);
        pointers.add(new NodePointer(leftNode));
        pointers.add(new NodePointer(rightNode));
        pageHeader.N = 2;
    }

    @Override
    boolean isLeaf() {
        return false;
    }

    @Override
    boolean isFull() {
        return pageHeader.N >= indexHeader.branchingFactor;
    }

    private void readFromPage() {
        // TODO
    }

    @Override
    void writeToPage() {
        byte[] headerBytes = pageHeader.toByteArray();
        System.arraycopy(headerBytes, 0, page.getData(), 0, HEADER_SIZE);

        // TODO write keys and pointers
    }

    @Override
    protected void dump0(PrintWriter out) {
        out.printf("Number of children: %d\n", pageHeader.N);

        for (int i = 0; i < pageHeader.N; i++) {
            out.printf("[%d]", pointers.get(i).getPageNum());
            if (i < pageHeader.N - 1) {
                out.printf(" %s ", keys.get(i));
            }
        }
        out.println();
    }
}
