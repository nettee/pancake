package me.nettee.pancake.core.index;

import com.google.common.base.Preconditions;
import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.page.Page;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class IndexNode {

    static final int HEADER_SIZE = 4;

    private static class Header {

        int numChildren;

        byte[] toByteArray() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream os = new DataOutputStream(baos);
            try {
                os.writeInt(numChildren);
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
    private final boolean root;
    private final boolean leaf;

    private Header pageHeader;
    private List<Attr> attrs;
    private List<Pointer> pointers;

    private IndexNode(Page page,
                      IndexHeader indexHeader,
                      boolean root,
                      boolean leaf) {
        this.page = page;
        this.indexHeader = indexHeader;
        this.root = root;
        this.leaf = leaf;
        pageHeader = new Header();
    }

    public static IndexNode create(Page page,
                                   IndexHeader indexHeader,
                                   boolean isRoot,
                                   boolean isLeaf) {
        IndexNode indexNode = new IndexNode(page, indexHeader, isRoot, isLeaf);
        indexNode.init();
        return indexNode;
    }

    private void init() {
        pageHeader.numChildren = 0;
        attrs = new ArrayList<>(indexHeader.branchingFactor - 1);
        pointers = new ArrayList<>(indexHeader.branchingFactor);
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

    public Page getPage() {
        return page;
    }

    public int getPageNum() {
        return page.getNum();
    }

    boolean isRoot() {
        return root;
    }

    boolean isLeaf() {
        return leaf;
    }

    boolean isEmpty() {
        return pageHeader.numChildren == 0;
    }

    boolean isFull() {
        return pageHeader.numChildren >= indexHeader.branchingFactor;
    }

    void writeToPage() {
        byte[] headerBytes = pageHeader.toByteArray();
        System.arraycopy(headerBytes, 0, page.getData(), 0, HEADER_SIZE);

        for (int i = 0; i < pointers.size(); i++) {
            int pos = HEADER_SIZE + i * (indexHeader.keyLength
                    + indexHeader.pointerLength);
            byte[] pointerBytes = pointers.get(i).getData();
            System.arraycopy(pointerBytes, 0, page.getData(), pos, indexHeader.pointerLength);
        }
        for (int i = 0; i < attrs.size(); i++) {
            int pos = HEADER_SIZE + i * (indexHeader.keyLength
                    + indexHeader.pointerLength) + indexHeader.pointerLength;
            byte[] attrBytes = attrs.get(i).getData();
            System.arraycopy(attrBytes, 0, page.getData(), pos, indexHeader.keyLength);
        }
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
            for (int i = 0; i < 3; i++) {
                out.printf("[%d]: %s, %s  ", i, attrs.get(i), pointers.get(i));
            }
            out.println("...");
            for (int i = pageHeader.numChildren - 3; i < pageHeader.numChildren; i++) {
                out.printf("[%d]: %s, %s  ", i, attrs.get(i), pointers.get(i));
            }
            out.println();
        }

        out.close();
        return baos.toString();
    }
}
