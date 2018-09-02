package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.page.Page;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class IndexNode {

    static final int HEADER_SIZE = 12;

    private static class Header {

        int numChildren;
        boolean isRoot;
        boolean isLeaf;
        short padding = (short) 0xeeee; // TODO for debug
        int padding2 = 0xeeeeeeee; // TODO for debug

        void fromByteArray(byte[] src) {
            checkState(src.length == HEADER_SIZE);
            ByteArrayInputStream bais = new ByteArrayInputStream(src);
            DataInputStream is = new DataInputStream(bais);
            try {
                numChildren = is.readInt();
                isRoot = is.readBoolean();
                isLeaf = is.readBoolean();
                is.readShort();
                is.readInt();
            } catch (IOException e) {
                throw new IndexException(e);
            }
        }

        byte[] toByteArray() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream os = new DataOutputStream(baos);
            try {
                os.writeInt(numChildren);
                os.writeBoolean(isRoot);
                os.writeBoolean(isLeaf);
                os.writeShort(padding);
                os.writeInt(padding2);
                byte[] data = baos.toByteArray();
                checkState(data.length == HEADER_SIZE);
                return data;
            } catch (IOException e) {
                throw new IndexException(e);
            }
        }

    }

    private final Page page;
    private final IndexHeader indexHeader;

    private Header pageHeader;
    private List<Attr> attrs;
    private List<Pointer> pointers;

    private IndexNode(Page page, IndexHeader indexHeader) {
        this.page = page;
        this.indexHeader = indexHeader;
        pageHeader = new Header();
        attrs = new ArrayList<>(indexHeader.branchingFactor - 1);
        pointers = new ArrayList<>(indexHeader.branchingFactor);
    }

    public static IndexNode create(Page page,
                                   IndexHeader indexHeader,
                                   boolean isRoot,
                                   boolean isLeaf) {
        IndexNode indexNode = new IndexNode(page, indexHeader);
        indexNode.init(isRoot, isLeaf);
        return indexNode;
    }

    public static IndexNode open(Page page, IndexHeader indexHeader) {
        IndexNode indexNode = new IndexNode(page, indexHeader);
        indexNode.load();
        return indexNode;
    }

    private void init(boolean isRoot, boolean isLeaf) {
        pageHeader.numChildren = 0;
        pageHeader.isRoot = isRoot;
        pageHeader.isLeaf = isLeaf;
    }

    private void load() {
        readFromPage();
    }

    void bpInsert(Attr attr, RID rid) {
        checkState(pageHeader.numChildren < indexHeader.branchingFactor);

        // Find insertion point i (0 <= i <= numChildren).
        int i;
        for (i = 0; i < pageHeader.numChildren; i++) {
            if (attr.compareTo(attrs.get(i)) < 0) {
                break;
            }
        }

        attrs.add(i, attr);
        pointers.add(i, Pointer.fromRid(rid));
        pageHeader.numChildren++;
    }

    public int getPageNum() {
        return page.getNum();
    }

    boolean isRoot() {
        return pageHeader.isRoot;
    }

    boolean isLeaf() {
        return pageHeader.isLeaf;
    }

    boolean isEmpty() {
        return pageHeader.numChildren == 0;
    }

    boolean isFull() {
        return pageHeader.numChildren >= indexHeader.branchingFactor;
    }

    private void readFromPage() {
        byte[] headerBytes = Arrays.copyOf(page.getData(), HEADER_SIZE);
        pageHeader.fromByteArray(headerBytes);

        for (int i = 0; i < pageHeader.numChildren; i++) {
            byte[] pointerBytes = Arrays.copyOfRange(page.getData(), pointerPos(i),
                    pointerPos(i) + indexHeader.pointerLength);
            Pointer pointer = Pointer.fromBytes(pointerBytes);
            pointers.add(pointer);
        }
        for (int i = 0; i < pageHeader.numChildren; i++) {
            byte[] attrBytes = Arrays.copyOfRange(page.getData(), attrPos(i),
                    attrPos(i) + indexHeader.keyLength);
            Attr attr = Attr.fromBytes(indexHeader.attrType, attrBytes);
            attrs.add(attr);
        }

    }

    void writeToPage() {
        byte[] headerBytes = pageHeader.toByteArray();
        System.arraycopy(headerBytes, 0, page.getData(), 0, HEADER_SIZE);

        for (int i = 0; i < pointers.size(); i++) {
            byte[] pointerBytes = pointers.get(i).getData();
            System.arraycopy(pointerBytes, 0,
                    page.getData(), pointerPos(i),
                    indexHeader.pointerLength);
        }
        for (int i = 0; i < attrs.size(); i++) {
            byte[] attrBytes = attrs.get(i).getData();
            System.arraycopy(attrBytes, 0,
                    page.getData(), attrPos(i),
                    indexHeader.keyLength);
        }
    }

    private int pointerPos(int i) {
        return HEADER_SIZE + i * (indexHeader.keyLength
                + indexHeader.pointerLength);
    }

    private int attrPos(int i) {
        return HEADER_SIZE + i * (indexHeader.keyLength
                + indexHeader.pointerLength) + indexHeader.pointerLength;
    }

    // For debug only.
    String dump() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);

        out.printf("Page[%d] - %s, %s\n", getPageNum(),
                isRoot() ? "root" : "non-root",
                isLeaf() ? "leaf" : "non-leaf"
        );
        out.printf("Number of children: %d\n", pageHeader.numChildren);

        if (isLeaf()) {
            if (pageHeader.numChildren < 5) {
                for (int i = 0; i < pageHeader.numChildren; i++) {
                    out.printf("[%d]: %s, %s  ", i, attrs.get(i), pointers.get(i));
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    out.printf("[%d]: %s, %s  ", i, attrs.get(i), pointers.get(i));
                }
                out.println("...");
                for (int i = pageHeader.numChildren - 3; i < pageHeader.numChildren; i++) {
                    out.printf("[%d]: %s, %s  ", i, attrs.get(i), pointers.get(i));
                }
                out.println();
            }
        }

        out.close();
        return baos.toString();
    }

}
