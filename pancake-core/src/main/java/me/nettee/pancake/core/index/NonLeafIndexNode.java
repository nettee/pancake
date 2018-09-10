package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.page.Page;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class NonLeafIndexNode extends IndexNode {

    private List<Attr> keys;
    private List<NodePointer> pointers;

    private NonLeafIndexNode(Page page, IndexHeader indexHeader) {
        super(page, indexHeader);
        keys = new ArrayList<>(indexHeader.branchingFactor - 1);
        pointers = new ArrayList<>(indexHeader.branchingFactor);
    }

    private NonLeafIndexNode(Page page, IndexHeader indexHeader, Header pageHeader) {
        super(page, indexHeader, pageHeader);
        keys = new ArrayList<>(indexHeader.branchingFactor - 1);
        pointers = new ArrayList<>(indexHeader.branchingFactor);
    }

    public static NonLeafIndexNode create(Page page,
                                       IndexHeader indexHeader,
                                       boolean isRoot) {
        NonLeafIndexNode node = new NonLeafIndexNode(page, indexHeader);
        node.init(isRoot, false);
        return node;
    }

    public static NonLeafIndexNode open(Page page, IndexHeader indexHeader, Header pageHeader) {
        checkArgument(!pageHeader.isLeaf);
        NonLeafIndexNode node = new NonLeafIndexNode(page, indexHeader, pageHeader);
        node.load();
        return node;
    }

    private void load() {
        readFromPage();
    }

    void addFirstTwoChildren(IndexNode first, IndexNode second) {
        checkState(isEmpty());
        keys.add(second.getFirstAttr());
        pointers.add(new NodePointer(first.getPageNum()));
        pointers.add(new NodePointer(second.getPageNum()));
        pageHeader.N = 2;
    }

    void addChild(IndexNode node) {
        checkState(!isEmpty());
        Attr key = node.getFirstAttr();
        NodePointer pointer = new NodePointer(node.getPageNum());
        int c = Collections.binarySearch(keys, key);
        int i = c >= 0 ? c : -c - 1; // Insertion point
        keys.add(i, key);
        pointers.add(i + 1, pointer);
        pageHeader.N++;
    }

    int findChild(Attr key) {
        checkState(!isEmpty());
        for (int i = 0; i < keys.size(); i++) {
            if (key.compareTo(keys.get(i)) < 0) {
                return pointers.get(i).getPageNum();
            }
        }
        checkState(pointers.size() == keys.size() + 1);
        return pointers.get(keys.size()).getPageNum();
    }

    void split(NonLeafIndexNode sibling) {
        throw new AssertionError();
    }

    @Override
    boolean isLeaf() {
        return false;
    }

    @Override
    boolean isFull() {
        return pageHeader.N >= indexHeader.branchingFactor;
    }

    @Override
    boolean isOverflow() {
        return pageHeader.N > indexHeader.branchingFactor;
    }

    @Override
    Attr getFirstAttr() {
        return keys.get(0);
    }

    private void readFromPage() {
        for (int i = 0; i < pageHeader.N - 1; i++) {
            byte[] keyBytes = Arrays.copyOfRange(page.getData(), attrPos(i),
                    attrPos(i) + indexHeader.keyLength);
            Attr key = Attr.fromBytes(indexHeader.attrType, keyBytes);
            keys.add(key);
        }
        for (int i = 0; i < pageHeader.N; i++) {
            byte[] pointerBytes = Arrays.copyOfRange(page.getData(), pointerPos(i),
                    pointerPos(i) + indexHeader.pointerLength);
            NodePointer pointer = NodePointer.fromBytes(pointerBytes);
            pointers.add(pointer);
        }
    }

    @Override
    protected void writeToPage0() {
        for (int i = 0; i < keys.size(); i++) {
            byte[] keyBytes = keys.get(i).toBytes();
            System.arraycopy(keyBytes, 0,
                    page.getData(), attrPos(i),
                    indexHeader.keyLength);
        }
        for (int i = 0; i < pointers.size(); i++) {
            byte[] pointerBytes = pointers.get(i).toBytes();
            System.arraycopy(pointerBytes, 0,
                    page.getData(), pointerPos(i),
                    indexHeader.pointerLength);
        }
    }

    @Override
    protected void dump0(PrintWriter out) {
        out.printf("Number of children: %d\n", pageHeader.N);
        out.printf("Children: %s\n", pointers.stream()
                .map(NodePointer::getPageNum)
                .map(String::valueOf)
                .collect(Collectors.joining(", ")));

        for (int i = 0; i < pageHeader.N; i++) {
            out.printf("[%d]", pointers.get(i).getPageNum());
            if (i < pageHeader.N - 1) {
                out.printf(" %s ", keys.get(i));
            }
        }
        out.println();
    }
}
