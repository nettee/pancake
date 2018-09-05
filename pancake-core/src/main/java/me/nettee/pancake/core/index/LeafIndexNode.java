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
    private Pointer rightPointer;

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
        LeafIndexNode indexNode = new LeafIndexNode(page, indexHeader);
        indexNode.init(isRoot, true);
        return indexNode;
    }

    public static LeafIndexNode open(Page page, IndexHeader indexHeader, Header pageHeader) {
        checkArgument(pageHeader.isLeaf);
        LeafIndexNode indexNode = new LeafIndexNode(page, indexHeader, pageHeader);
        indexNode.load();
        return indexNode;
    }

    private void load() {
        readFromPage();
    }

    void insert(Attr attr, RID rid) {
        checkState(!isFull());

        // Find insertion point i (0 <= i <= N).
        int i;
        for (i = 0; i < pageHeader.N; i++) {
            if (attr.compareTo(attrs.get(i)) < 0) {
                break;
            }
        }

        attrs.add(i, attr);
        rids.add(i, rid);
        pageHeader.N++;
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
    String dump() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);

        out.printf("Page[%d] - %s, %s\n", getPageNum(),
                isRoot() ? "root" : "non-root",
                isLeaf() ? "leaf" : "non-leaf"
        );
        out.printf("Number of children: %d\n", pageHeader.N);

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

        out.close();
        return baos.toString();
    }

}
