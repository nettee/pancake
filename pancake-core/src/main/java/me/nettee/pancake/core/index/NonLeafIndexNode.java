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

/**
 * The layout of non-leaf index node:
 * Let b = branching factor, we have b-1 keys and b pointers.
 */
public class NonLeafIndexNode extends IndexNode {

    List<Attr> keys; // size: b-1
    List<NodePointer> pointers; // size: b

    private NonLeafIndexNode(Page page, IndexHeader indexHeader) {
        super(page, indexHeader);
        keys = new ArrayList<>(indexHeader.branchingFactor - 1);
        pointers = new ArrayList<>(indexHeader.branchingFactor);
    }

    private NonLeafIndexNode(Page page, IndexHeader indexHeader, IndexNodeHeader pageIndexNodeHeader) {
        super(page, indexHeader, pageIndexNodeHeader);
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

    public static NonLeafIndexNode open(Page page, IndexHeader indexHeader, IndexNodeHeader pageIndexNodeHeader) {
        checkArgument(!pageIndexNodeHeader.isLeaf);
        NonLeafIndexNode node = new NonLeafIndexNode(page, indexHeader, pageIndexNodeHeader);
        node.load();
        return node;
    }

    private void load() {
        readFromPage();
    }

    void addFirstTwoChildren(IndexNode first, IndexNode second, Attr upKey) {
        checkState(isEmpty());
        keys.add(upKey);
        pointers.add(new NodePointer(first.getPageNum()));
        pointers.add(new NodePointer(second.getPageNum()));
        indexNodeHeader.N = 2;
    }

    void addChild(IndexNode node, Attr upKey) {
        checkState(!isEmpty());
        NodePointer pointer = new NodePointer(node.getPageNum());
        int c = Collections.binarySearch(keys, upKey);
        int i = c >= 0 ? c : -c - 1; // Insertion point
        keys.add(i, upKey);
        pointers.add(i + 1, pointer);
        indexNodeHeader.N++;
    }

    int findChild(Attr key) {
        checkState(!isEmpty());
        // TODO use binary search
        for (int i = 0; i < keys.size(); i++) {
            if (key.compareTo(keys.get(i)) < 0) {
                return pointers.get(i).getPageNum();
            }
        }
        checkState(pointers.size() == keys.size() + 1);
        return pointers.get(keys.size()).getPageNum();
    }

    Attr split(NonLeafIndexNode sibling) {
        checkState(isOverflow());
        checkState(keys.size() == pointers.size() - 1);

//        System.out.printf("Split non-leaf node [%d] with sibling [%d]\n",
//                getPageNum(), sibling.getPageNum());

        int curSize = pointers.size();
        int newSize = curSize / 2;

        // Keep pointers [0, newSize),
        // move pointers [newSize, curSize) to sibling.
        // Keep keys [0, newSize-1),
        // move keys [newSize, curSize-1) to sibling.
        // Pull key[newSize-1] up.

        sibling.pointers.addAll(pointers.subList(newSize, curSize));
        pointers.subList(newSize, curSize).clear();

        sibling.keys.addAll(keys.subList(newSize, curSize-1));
        keys.subList(newSize, curSize-1).clear();

        Attr upKey = keys.remove(newSize - 1);

        indexNodeHeader.N = pointers.size();
        sibling.indexNodeHeader.N = sibling.pointers.size();

//        System.out.printf("Up key %s\n", upKey.toSimplifiedString());

        return upKey;
    }

    @Override
    boolean isLeaf() {
        return false;
    }

    @Override
    boolean isFull() {
        return indexNodeHeader.N >= indexHeader.branchingFactor;
    }

    @Override
    boolean isOverflow() {
        return indexNodeHeader.N > indexHeader.branchingFactor;
    }

    @Override
    Attr getFirstAttr() {
        return keys.get(0);
    }

    private void readFromPage() {
        for (int i = 0; i < indexNodeHeader.N - 1; i++) {
            byte[] keyBytes = Arrays.copyOfRange(page.getData(), attrPos(i),
                    attrPos(i) + indexHeader.keyLength);
            Attr key = Attr.fromBytes(indexHeader.attrType, keyBytes);
            keys.add(key);
        }
        for (int i = 0; i < indexNodeHeader.N; i++) {
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

    void check() {
        checkState(!isLeaf());
        checkState(!isOverflow());
        checkState(pointers.size() == indexNodeHeader.N);
        checkState(keys.size() == indexNodeHeader.N - 1);
        for (int i = 1; i < keys.size(); i++) {
            Attr a1 = keys.get(i - 1);
            Attr a2 = keys.get(i);
            checkState(a1.compareTo(a2) < 0);
        }
    }

    @Override
    protected void dump0(PrintWriter out, boolean verbose) {
        out.printf("Number of children: %d%n", indexNodeHeader.N);
        out.printf("Children: %s%n", pointers.stream()
                .map(NodePointer::getPageNum)
                .map(String::valueOf)
                .collect(Collectors.joining(", ")));

        for (int i = 0; i < indexNodeHeader.N; i++) {
            out.printf("[%d]", pointers.get(i).getPageNum());
            if (i < indexNodeHeader.N - 1) {
                out.print(" ");
                out.print(keys.get(i).toSimplifiedString());
                out.print(" ");
            }
        }
        out.println();
    }
}
