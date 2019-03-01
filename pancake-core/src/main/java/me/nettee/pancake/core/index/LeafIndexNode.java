package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.model.StringAttr;
import me.nettee.pancake.core.page.Page;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * The layout of leaf index node:
 * Let b = branching factor, we have b-1 attrs (i.e. keys), b-1 rids (i.e.
 * values), and one right pointer. Note that the values have the same size as
 * pointers in non-leaf nodes. So b rids, together with one right pointer,
 * occupy the same size as b pointers in non-leaf nodes.
 */
public class LeafIndexNode extends IndexNode {

    private List<Attr> attrs; // size: b-1
    private List<RID> rids; // size: b-1
    private NodePointer rightPointer;

    private LeafIndexNode(Page page, IndexHeader indexHeader) {
        super(page, indexHeader);
        attrs = new ArrayList<>(indexHeader.branchingFactor - 1);
        rids = new ArrayList<>(indexHeader.branchingFactor - 1);
        rightPointer = null;
    }

    private LeafIndexNode(Page page, IndexHeader indexHeader, IndexNodeHeader pageIndexNodeHeader) {
        super(page, indexHeader, pageIndexNodeHeader);
        attrs = new ArrayList<>(indexHeader.branchingFactor - 1);
        rids = new ArrayList<>(indexHeader.branchingFactor - 1);
        rightPointer = null;
    }

    public static LeafIndexNode create(Page page,
                                       IndexHeader indexHeader,
                                       boolean isRoot) {
        LeafIndexNode node = new LeafIndexNode(page, indexHeader);
        node.init(isRoot, true);
        return node;
    }

    public static LeafIndexNode open(Page page, IndexHeader indexHeader, IndexNodeHeader pageIndexNodeHeader) {
        checkArgument(pageIndexNodeHeader.isLeaf);
        LeafIndexNode node = new LeafIndexNode(page, indexHeader, pageIndexNodeHeader);
        node.load();
        return node;
    }

    private void load() {
        readFromPage();
    }

    private void insert0(Attr attr, RID rid) {
        // Find insertion point i (0 <= i <= N).
        // i == N means the new attr should be append to the last.
        // TODO use binary search
        int i;
        for (i = 0; i < indexNodeHeader.N; i++) {
            int c = attr.compareTo(attrs.get(i));
            if (c < 0) {
                break;
            } else if (c == 0) {
                // Duplicated key found.
                throw new IndexException("Duplicated key: " + attr.toString());
            }
        }

        attrs.add(i, attr);
        rids.add(i, rid);
    }

    // We can insert one more entry when the node is full. The overflowed
    // node will be split instantly.
    void insert(Attr attr, RID rid) {
        checkState(!isOverflow());
        insert0(attr, rid);
        indexNodeHeader.N++;
    }

    Attr split(LeafIndexNode sibling) {
        checkState(isOverflow());
        checkState(attrs.size() == rids.size());

//        System.out.printf("Split leaf node [%d] with sibling [%d]\n",
//                getPageNum(), sibling.getPageNum());

        int curSize = attrs.size();
        int newSize = curSize / 2;

        // Move the last half to sibling
        sibling.attrs.addAll(attrs.subList(newSize, curSize));
        attrs.subList(newSize, curSize).clear();
        Attr upKey = sibling.attrs.get(0);

//        System.out.printf("Up key: %s\n", upKey.toSimplifiedString());

        sibling.rids.addAll(rids.subList(newSize, curSize));
        rids.subList(newSize, curSize).clear();

        indexNodeHeader.N = attrs.size();
        sibling.indexNodeHeader.N = sibling.attrs.size();

        // TODO Set right pointer

        return upKey;
    }

    @Override
    boolean isLeaf() {
        return true;
    }

    // Note: this is one less than that in non-leaf nodes
    @Override
    boolean isFull() {
        return indexNodeHeader.N >= indexHeader.branchingFactor - 1;
    }

    // Note: this is one less than that in non-leaf nodes
    @Override
    boolean isOverflow() {
        return indexNodeHeader.N > indexHeader.branchingFactor - 1;
    }

    @Override
    Attr getFirstAttr() {
        checkState(!isEmpty());
        return attrs.get(0);
    }

    private void readFromPage() {
        for (int i = 0; i < indexNodeHeader.N; i++) {
            byte[] attrBytes = Arrays.copyOfRange(page.getData(), attrPos(i),
                    attrPos(i) + indexHeader.keyLength);
            Attr attr = Attr.fromBytes(indexHeader.attrType, attrBytes);
            attrs.add(attr);
        }
        for (int i = 0; i < indexNodeHeader.N; i++) {
            byte[] ridBytes = Arrays.copyOfRange(page.getData(), pointerPos(i),
                    pointerPos(i) + indexHeader.pointerLength);
            RID rid = RID.fromBytes(ridBytes);
            rids.add(rid);
        }
        // TODO read right pointer
    }

    @Override
    protected void writeToPage0() {
        for (int i = 0; i < attrs.size(); i++) {
            byte[] attrBytes = attrs.get(i).toBytes();
            System.arraycopy(attrBytes, 0,
                    page.getData(), attrPos(i),
                    indexHeader.keyLength);
        }
        for (int i = 0; i < rids.size(); i++) {
            byte[] ridBytes = rids.get(i).toBytes();
            System.arraycopy(ridBytes, 0,
                    page.getData(), pointerPos(i),
                    indexHeader.pointerLength);
        }
        // TODO write right pointer
    }

    @Override
    protected void dump0(PrintWriter out, boolean verbose) {
        out.printf("Number of attrs: %d%n", indexNodeHeader.N);

        if (!isLeaf()) {
            return;
        }

        IntFunction<String> f = i -> String.format("[%d]: %s, %s", i,
                attrs.get(i).toSimplifiedString(), rids.get(i));

        if (verbose || indexNodeHeader.N < 5) {
            for (int i = 0; i < indexNodeHeader.N; i++) {
                out.print(f.apply(i));
                out.print("  ");
                if (i != indexNodeHeader.N - 1 && i % 5 == 4) {
                    out.println();
                }
            }
            out.println();
        } else {
            for (int i = 0; i < 3; i++) {
                out.print(f.apply(i));
                out.print("  ");
            }
            out.print("... ");
            for (int i = indexNodeHeader.N - 3; i < indexNodeHeader.N; i++) {
                out.print(f.apply(i));
                out.print("  ");
            }
            out.println();
        }
    }

}
