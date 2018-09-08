package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.page.Page;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class LeafIndexNode extends IndexNode {

    private List<Attr> attrs;
    private List<RID> rids;
    private NodePointer rightPointer;

    private LeafIndexNode(Page page, IndexHeader indexHeader) {
        super(page, indexHeader);
        attrs = new ArrayList<>(indexHeader.branchingFactor - 1);
        rids = new ArrayList<>(indexHeader.branchingFactor - 1);
        rightPointer = null;
    }

    private LeafIndexNode(Page page, IndexHeader indexHeader, Header pageHeader) {
        super(page, indexHeader,pageHeader);
        attrs = new ArrayList<>(indexHeader.branchingFactor - 1);
        rids = new ArrayList<>(indexHeader.branchingFactor - 1);
        rightPointer = null;
    }

    public static LeafIndexNode create(Page page,
                                       IndexHeader indexHeader,
                                       boolean isRoot) {
        LeafIndexNode node = new LeafIndexNode(page, indexHeader);
        node.init(isRoot, true);
        return node;
    }

    public static LeafIndexNode open(Page page, IndexHeader indexHeader, Header pageHeader) {
        checkArgument(pageHeader.isLeaf);
        LeafIndexNode node = new LeafIndexNode(page, indexHeader, pageHeader);
        node.load();
        return node;
    }

    private void load() {
        readFromPage();
    }

    private void insert0(Attr attr, RID rid) {
        // Find insertion point i (0 <= i <= N).
        int i;
        for (i = 0; i < pageHeader.N; i++) {
            if (attr.compareTo(attrs.get(i)) < 0) {
                break;
            }
        }

        attrs.add(i, attr);
        rids.add(i, rid);
    }

    // Add the first n elements in src to dest1, and others to dest2.
    private <E> void split0(List<E> src, List<E> other, int n) {
        int N = src.size();
        for (int i = n; i < N; i++) {
            other.add(src.get(i));
        }
        for (int i = N - 1; i >= n; i--) {
            src.remove(i);
        }
    }

    void insert(Attr attr, RID rid) {
        checkState(!isFull());
        insert0(attr, rid);
        pageHeader.N++;
    }

    void insertAndSplit(Attr attr, RID rid,
                                    LeafIndexNode otherNode,
                                    NonLeafIndexNode parentNode) {
        checkState(isFull());
        insert0(attr, rid);

        checkState(attrs.size() == rids.size());
        int N = attrs.size();
        int n = N / 2;

        split0(attrs, otherNode.attrs, n);
        split0(rids, otherNode.rids, n);

        pageHeader.N = n;
        otherNode.pageHeader.N = N - n;

        parentNode.addTwoChildren(otherNode.attrs.get(0),
                this.getPageNum(),
                otherNode.getPageNum());

        // TODO Set right pointer
    }

    @Override
    boolean isLeaf() {
        return true;
    }

    @Override
    boolean isFull() {
        return pageHeader.N >= indexHeader.branchingFactor - 1;
    }

    private void readFromPage() {
        for (int i = 0; i < pageHeader.N; i++) {
            byte[] attrBytes = Arrays.copyOfRange(page.getData(), attrPos(i),
                    attrPos(i) + indexHeader.keyLength);
            Attr attr = Attr.fromBytes(indexHeader.attrType, attrBytes);
            attrs.add(attr);
        }
        for (int i = 0; i < pageHeader.N; i++) {
            byte[] ridBytes = Arrays.copyOfRange(page.getData(), pointerPos(i),
                    pointerPos(i) + indexHeader.pointerLength);
            RID rid = RID.fromBytes(ridBytes);
            rids.add(rid);
        }
        // TODO read right pointer
    }

    @Override
    void writeToPage() {
        byte[] headerBytes = pageHeader.toByteArray();
        System.arraycopy(headerBytes, 0, page.getData(), 0, HEADER_SIZE);

        for (int i = 0; i < attrs.size(); i++) {
            byte[] attrBytes = attrs.get(i).getData();
            System.arraycopy(attrBytes, 0,
                    page.getData(), attrPos(i),
                    indexHeader.keyLength);
        }
        for (int i = 0; i < rids.size(); i++) {
            byte[] ridBytes = rids.get(i).toBytes();
            System.arraycopy(ridBytes, 0,
                    page.getData(), pointerPos(i),
                    indexHeader.pointerLength);
        }
        // TODO write right pointer
    }

    @Override
    protected void dump0(PrintWriter out) {
        out.printf("Number of attrs: %d\n", pageHeader.N);

        if (isLeaf()) {
            if (pageHeader.N < 5) {
                for (int i = 0; i < pageHeader.N; i++) {
                    out.printf("[%d]: %s, %s  ", i, attrs.get(i), rids.get(i));
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    out.printf("[%d]: %s, %s  ", i, attrs.get(i), rids.get(i));
                }
                out.println("...");
                for (int i = pageHeader.N - 3; i < pageHeader.N; i++) {
                    out.printf("[%d]: %s, %s  ", i, attrs.get(i), rids.get(i));
                }
                out.println();
            }
        }
    }

}
