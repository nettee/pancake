package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.model.AttrType;
import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.page.Page;

public class IndexNode {

    private final Page page;
    private final boolean root;

    private IndexNode(Page page, boolean root) {
        this.page = page;
        this.root = root;
    }

    public static IndexNode create(Page page, boolean isRoot, AttrType attrType) {
        IndexNode indexNode = new IndexNode(page, isRoot);
        indexNode.init(attrType);
        return indexNode;
    }

    private void init(AttrType attrType) {
        // TODO
    }

    void bpInsert(Attr attr, RID rid) {
        // TODO
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
