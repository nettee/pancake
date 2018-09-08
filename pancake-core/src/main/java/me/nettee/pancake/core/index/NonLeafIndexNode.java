package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.page.Page;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

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

    void addTwoChildren(Attr key, int leftNode, int rightNode) {
        checkState(isEmpty());
        keys.add(key);
        pointers.add(new NodePointer(leftNode));
        pointers.add(new NodePointer(rightNode));
        pageHeader.N = 2;
    }

    Pair<Integer, Integer> findChild(Attr key) {
        final IntFunction<Pair<Integer, Integer>> f = i -> {
            int pageNum = pointers.get(i).getPageNum();
            return new ImmutablePair<>(i, pageNum);
        };
        checkState(!isEmpty());
        for (int i = 0; i < keys.size(); i++) {
            if (key.compareTo(keys.get(i)) < 0) {
                return f.apply(i);
            }
        }
        checkState(pointers.size() == keys.size() + 1);
        return f.apply(keys.size());
    }

    void alterChild(int i, int pageNum) {
        NodePointer pointer = new NodePointer(pageNum);
        pointers.set(i, pointer);
    }

    @Override
    boolean isLeaf() {
        return false;
    }

    @Override
    boolean isFull() {
        return pageHeader.N >= indexHeader.branchingFactor;
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

        for (int i = 0; i < pageHeader.N; i++) {
            out.printf("[%d]", pointers.get(i).getPageNum());
            if (i < pageHeader.N - 1) {
                out.printf(" %s ", keys.get(i));
            }
        }
        out.println();
    }
}
