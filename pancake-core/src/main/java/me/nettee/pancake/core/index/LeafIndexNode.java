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

    // We can insert one more entry when the node is full. The overflowed
    // node will be split instantly.
    void insert(Attr attr, RID rid) {
        checkState(!isOverflow());
        insert0(attr, rid);
        pageHeader.N++;
    }

    void split(LeafIndexNode sibling) {
        checkState(isOverflow());
        checkState(attrs.size() == rids.size());
        int N = attrs.size();
        int n = N / 2;

        sibling.attrs.addAll(attrs.subList(n, N));
        sibling.rids.addAll(rids.subList(n, N));

        attrs.subList(n, N).clear();
        rids.subList(n, N).clear();

        pageHeader.N = n;
        sibling.pageHeader.N = N - n;

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

    @Override
    boolean isOverflow() {
        return pageHeader.N > indexHeader.branchingFactor - 1;
    }

    @Override
    Attr getFirstAttr() {
        checkState(!isEmpty());
        return attrs.get(0);
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
    protected void writeToPage0() {
        for (int i = 0; i < attrs.size(); i++) {
            byte[] attrBytes = attrs.get(i).toBytes();
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
