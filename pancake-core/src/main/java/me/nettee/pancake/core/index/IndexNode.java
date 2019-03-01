package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.page.Page;

import java.io.*;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkState;

/**
 * There are two types of index nodes: leaf nodes and non-leaf nodes.
 * Both types of nodes share the same {@code Header}, but store different
 * types of data themselves.
 * <p>
 * Leaf nodes store keys and values (i.e. RIDs), whereas non-leaf nodes store
 * keys and pointers. However, the size of a pointer is set to be equal to the
 * size of an RID. Thus, leaf nodes and non-leaf nodes share the same branching
 * factor.
 *
 */
public abstract class IndexNode {

    static final int HEADER_SIZE = 12;

    protected static class IndexNodeHeader {

        /**
         * N: current size of index node, which should be <= branching factor.
         * A non-leaf node has N-1 keys (attrs) and N pointers.
         * A leaf node has N keys (attrs) and N values (RIDs).
         */
        int N;
        boolean isRoot;
        boolean isLeaf;
        // Pad the header to 12 bytes
        short padding = (short) 0xeeee;
        int padding2 = 0xeeeeeeee;

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
    protected IndexNodeHeader indexNodeHeader;

    protected IndexNode(Page page, IndexHeader indexHeader) {
        this.page = page;
        this.indexHeader = indexHeader;
        indexNodeHeader = new IndexNodeHeader();
    }

    protected IndexNode(Page page, IndexHeader indexHeader, IndexNodeHeader indexNodeHeader) {
        this.page = page;
        this.indexHeader = indexHeader;
        this.indexNodeHeader = indexNodeHeader;
    }

    public static LeafIndexNode createLeaf(Page page,
                                    IndexHeader indexHeader,
                                    boolean isRoot) {
        return LeafIndexNode.create(page, indexHeader, isRoot);
    }

    public static NonLeafIndexNode createNonLeaf(Page page,
                                          IndexHeader indexHeader,
                                          boolean isRoot) {
        return NonLeafIndexNode.create(page, indexHeader, isRoot);
    }

    public static IndexNode open(Page page,
                                 IndexHeader indexHeader) {

        IndexNodeHeader pageIndexNodeHeader = readHeaderFromPage(page);
        if (pageIndexNodeHeader.isLeaf) {
            return LeafIndexNode.open(page, indexHeader, pageIndexNodeHeader);
        } else {
            return NonLeafIndexNode.open(page, indexHeader, pageIndexNodeHeader);
        }
    }

    protected void init(boolean isRoot, boolean isLeaf) {
        indexNodeHeader.N = 0;
        indexNodeHeader.isRoot = isRoot;
        indexNodeHeader.isLeaf = isLeaf;
    }

    private static IndexNodeHeader readHeaderFromPage(Page page) {
        IndexNodeHeader pageIndexNodeHeader = new IndexNodeHeader();
        byte[] headerBytes = Arrays.copyOf(page.getData(), HEADER_SIZE);
        pageIndexNodeHeader.fromByteArray(headerBytes);
        return pageIndexNodeHeader;
    }

    public int getPageNum() {
        return page.getNum();
    }

    boolean isRoot() {
        return indexNodeHeader.isRoot;
    }

    abstract boolean isLeaf();

    boolean isEmpty() {
        return indexNodeHeader.N == 0;
    }

    abstract boolean isFull();

    abstract boolean isOverflow();

    abstract Attr getFirstAttr();

    static class SplitResult {

        final IndexNode sibling;
        final Attr upKey;

        SplitResult(IndexNode sibling, Attr upKey) {
            this.sibling = sibling;
            this.upKey = upKey;
        }
    }

    final void writeToPage() {
        byte[] headerBytes = indexNodeHeader.toByteArray();
        System.arraycopy(headerBytes, 0, page.getData(), 0, HEADER_SIZE);

        writeToPage0();
    }

    protected abstract void writeToPage0();

    protected int pointerPos(int i) {
        return HEADER_SIZE +
                i * (indexHeader.pointerLength + indexHeader.keyLength);
    }

    protected int attrPos(int i) {
        return HEADER_SIZE +
                i * (indexHeader.pointerLength + indexHeader.keyLength)
                + indexHeader.pointerLength;
    }

    String getNodeTypeString() {
        if (isRoot() && isLeaf()) {
            return "Single Root Node (root=true, leaf=true)";
        } else if (isRoot()) {
            return "Root Node (root=true, leaf=false)";
        } else if (isLeaf()) {
            return "Leaf Node (root=false, leaf=true)";
        } else {
            return "Internal Node (root=false, leaf=false)";
        }
    }

    abstract void check();

    // For debug only.
    final void dump(PrintWriter out, boolean verbose) {
        String s = String.format("Page[%d] - Node Type: %s",
                getPageNum(), getNodeTypeString());
        out.println(s);
        dump0(out, verbose);
    }

    protected abstract void dump0(PrintWriter out, boolean verbose);
}
