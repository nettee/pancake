package me.nettee.pancake.core.index;

import me.nettee.pancake.core.page.Page;

import java.io.*;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkState;

public abstract class IndexNode {

    static final int HEADER_SIZE = 12;

    protected static class Header {

        /**
         * A node has at most N-1 keys (attrs) and N pointers.
         */
        int N;
        boolean isRoot;
        boolean isLeaf;
        short padding = (short) 0xeeee; // TODO for debug
        int padding2 = 0xeeeeeeee; // TODO for debug

        void fromByteArray(byte[] src) {
            checkState(src.length == HEADER_SIZE);
            ByteArrayInputStream bais = new ByteArrayInputStream(src);
            DataInputStream is = new DataInputStream(bais);
            try {
                N = is.readInt();
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
                os.writeInt(N);
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

    protected Page page;
    protected IndexHeader indexHeader;
    protected Header pageHeader;

    protected IndexNode(Page page, IndexHeader indexHeader) {
        this.page = page;
        this.indexHeader = indexHeader;
        pageHeader = new Header();
    }

    protected IndexNode(Page page, IndexHeader indexHeader, Header pageHeader) {
        this.page = page;
        this.indexHeader = indexHeader;
        this.pageHeader = pageHeader;
    }

    public static LeafIndexNode createLeaf(Page page,
                                    IndexHeader indexHeader,
                                    boolean isRoot) {
        return LeafIndexNode.create(page, indexHeader, isRoot);
    }

    public static NonLeafIndexNode createNonLeaf(Page page,
                                          IndexHeader indexHeader,
                                          boolean isRoot) {
        return null;
    }

    public static IndexNode open(Page page,
                                 IndexHeader indexHeader) {

        Header pageHeader = readHeaderFromPage(page);
        if (pageHeader.isLeaf) {
            return LeafIndexNode.open(page, indexHeader, pageHeader);
        } else {
            return NonLeafIndexNode.open(page, indexHeader, pageHeader);
        }
    }

    protected void init(boolean isRoot, boolean isLeaf) {
        pageHeader.N = 0;
        pageHeader.isRoot = isRoot;
        pageHeader.isLeaf = isLeaf;
    }

    private static Header readHeaderFromPage(Page page) {
        Header pageHeader = new Header();
        byte[] headerBytes = Arrays.copyOf(page.getData(), HEADER_SIZE);
        pageHeader.fromByteArray(headerBytes);
        return pageHeader;
    }

    public int getPageNum() {
        return page.getNum();
    }

    boolean isRoot() {
        return pageHeader.isRoot;
    }

    abstract boolean isLeaf();

    boolean isEmpty() {
        return pageHeader.N == 0;
    }

    abstract boolean isFull();

    abstract void writeToPage();

    // For debug only.
    abstract String dump();
}
