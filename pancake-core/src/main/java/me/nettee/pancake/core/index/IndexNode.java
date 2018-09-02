package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.page.Page;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class IndexNode {

    static final int HEADER_SIZE = 4;

    private static class Header {

        int numChildren;

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
        if (pageHeader.numChildren >= indexHeader.branchingFactor) {
            // This node is full
            // TODO
            return;
        }
        System.out.printf("Inserting attr <%s>\n", attr);
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

    public boolean isRoot() {
        return root;
    }

    public boolean isLeaf() {
        return leaf;
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
            for (int i = 0; i < pageHeader.numChildren; i++) {
                out.printf("[%d]: %s, %s\n", i, attrs.get(i), pointers.get(i));
            }
        }

        out.close();
        return baos.toString();
    }
}
