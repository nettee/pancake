package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.page.Page;

import java.util.ArrayList;
import java.util.List;

public class IndexNode {

    static final int HEADER_SIZE = 4;

    private static class Header {

        int numChildren;

    }

    private final Page page;
    private final boolean root;
    private final IndexHeader indexHeader;

    private Header pageHeader;
    private List<Attr> attrs;
    private List<Pointer> pointers;

    private IndexNode(Page page, boolean root, IndexHeader indexHeader) {
        this.page = page;
        this.root = root;
        this.indexHeader = indexHeader;
        pageHeader = new Header();
    }

    public static IndexNode create(Page page, boolean isRoot, IndexHeader indexHeader) {
        IndexNode indexNode = new IndexNode(page, isRoot, indexHeader);
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
        int i;
        for (i = 0; i < pageHeader.numChildren; i++) {
            if (attr.compareTo(attrs.get(i)) < 0) {
                break;
            }
        }
        // 0 <= i <= numChildren
        attrs.add(i, attr);
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
}
