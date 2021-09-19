package me.nettee.pancake.core.index;

import me.nettee.pancake.core.index.IndexNode.SplitResult;
import me.nettee.pancake.core.model.*;
import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.*;

/**
 * {@code Index} object is used to handle index files, i.e. insert and delete
 * index entries.
 *
 * We implement the B+ tree data structure. Reference
 * <a href="https://en.wikipedia.org/wiki/B%2B_tree">Wikipedia</a> for terms.
 * <p>
 * Split strategy for B+ tree:
 * Let b = branching factor. A leaf node can have at most b-1 key-value pairs.
 * A non-leaf node can have at most b pointers (i.e. children) and b-1 keys.
 * When inserting to a full node, the node overflows, and should split to two
 * nodes (itself and its sibling), and pull up a key to its parent.
 * When a leaf node splits, its key-value pairs are divided evenly into two
 * parts. The node itself keeps the left part, and its new sibling takes the
 * right part. The node copies the first key of its sibling and pull it up to
 * the parent node.
 * When a non-leaf node splits, its pointers are divided evenly into two parts.
 * The node itself keeps the left part of pointers (and the corresponding keys),
 * and its new sibling takes the right part of pointers (and the corresponding
 * keys). However, in this case, one key will have nowhere to place. So the key
 * will be removed (not copied), and pulled up to the parent node.
 * For either cases, if the splitting node is root itself, a new root will be
 * created. The new root will have two children (the splitting node and its
 * sibling).
 */
public class Index {

    private static Logger logger = LoggerFactory.getLogger(Index.class);

    private static class IndexNodeBuffer {

        private Map<Integer, IndexNode> buf = new HashMap<>();

        void add(IndexNode indexNode) {
            int pageNum = indexNode.getPageNum();
            buf.put(pageNum, indexNode);
        }

        boolean contains(int pageNum) {
            return buf.containsKey(pageNum);
        }

        IndexNode get(int pageNum) {
            return buf.get(pageNum);
        }

        Collection<IndexNode> nodes() {
            return buf.values();
        }
    }

    private final PagedFile pagedFile;
    private final Path indexFile;

    private IndexHeader header;
    private IndexNodeBuffer buffer;
    private boolean open;

    private Index(PagedFile pagedFile, Path indexFile) {
        this.pagedFile = pagedFile;
        this.indexFile = indexFile;
        header = new IndexHeader();
        buffer = new IndexNodeBuffer();
        open = true;
    }

    /**
     * Create an index numbered {@code indexNo} on {@code dataFile}. The data
     * file should be a valid record file. Each index associates with a specific
     * attribute in the record file. The {@code indexNo} is a number assigned to
     * this index to distinguish indexes on different attributes. Callers should
     * ensure that {@code indexNo} is unique and non-negative for each index
     * created on a record file. However, {@code indexNo} is not necessarily be
     * sequential. This method will create an index file, and return an
     * {@code Index} object to handle the index file.
     * @param dataFile the record file name
     * @param indexNo the index number
     * @param attrType the type of the attribute to be indexed
     * @return the created {@code Index} object
     */
    public static Index create(Path dataFile, int indexNo, AttrType attrType) {
        checkNotNull(dataFile);
        checkArgument(Files.exists(dataFile), messageDataFileNotExist(dataFile));
        checkArgument(indexNo >= 0, messageNegativeIndexNo(indexNo));
        checkNotNull(attrType);

        logger.info("Creating index {} on data file {}", indexNo, dataFile.toString());

        Path indexFile = joinIndexFile(dataFile, indexNo);
        // Duplicated indexNo will fail on this step.
        PagedFile pagedFile = PagedFile.create(indexFile);
        checkState(pagedFile.getNumOfPages() == 0,
                "Created page file is not empty");
        pagedFile.allocatePage(); // As header page

        Index index = new Index(pagedFile, indexFile);
        index.header.init(attrType);
        logger.info("Index header initialized");

        return index;
    }

    /**
     * Destroy the index numbered {@code indexNo} on {@code dataFile}. The index
     * file will be removed, but the record file will be reserved.
     *
     * @param dataFile the record file name
     * @param indexNo the index number
     */
    public static void destroy(Path dataFile, int indexNo) {
        checkNotNull(dataFile);
        checkArgument(Files.exists(dataFile), messageDataFileNotExist(dataFile));
        checkArgument(indexNo >= 0, messageNegativeIndexNo(indexNo));

        logger.info("Destroying index {} on data file {}", indexNo, dataFile.toString());

        Path indexFile = joinIndexFile(dataFile, indexNo);
        checkIndexFileExistance(indexFile, dataFile, indexNo);

        try {
            Files.delete(indexFile);
        } catch (IOException e) {
            throw new IndexException(e);
        }
    }

    /**
     * Open an index numbered {@code indexNo} on {@code dataFile}. The data file
     * should be a valid record file, and the index should be created before.
     * This method will return an {@code Index} object to handle the index file.
     *
     * @param dataFile the data file, storing records
     * @param indexNo the index number
     * @return the created {@code Index} object
     */
    public static Index open(Path dataFile, int indexNo) {
        checkNotNull(dataFile);
        checkArgument(Files.exists(dataFile), messageDataFileNotExist(dataFile));
        checkArgument(indexNo >= 0, messageNegativeIndexNo(indexNo));

        logger.info("Opening index {} on data file {}", indexNo, dataFile.toString());

        Path indexFile = joinIndexFile(dataFile, indexNo);
        checkIndexFileExistance(indexFile, dataFile, indexNo);

        PagedFile pagedFile = PagedFile.open(indexFile);
        checkState(pagedFile.getNumOfPages() > 0,
                "Opened page file is empty");

        Index index = new Index(pagedFile, indexFile);
        Page headerPage = pagedFile.getFirstPage();
        index.header.readFrom(headerPage.getData());
        pagedFile.unpinPage(headerPage);
        logger.info("Index header loaded");

        return index;
    }

    private static Path joinIndexFile(Path dataFile, int indexNo) {
        String indexFileName = String.format("%s.%d",
                dataFile.getFileName().toString(), indexNo);
        return dataFile.getParent().resolve(indexFileName);
    }

    private static void checkIndexFileExistance(Path indexFile, Path dataFile, int indexNo) {
        if (Files.notExists(indexFile)) {
            String msg = String.format("Cannot find index %d on data file %s",
                    indexNo, dataFile.toAbsolutePath().toString());
            throw new IndexException(msg);
        }
    }

    private static String messageDataFileNotExist(Path dataFile) {
        return "Data file does not exist: " + dataFile.toString();
    }

    private static String messageNegativeIndexNo(int indexNo) {
        return "IndexNo should be non-negative: " + indexNo;
    }

    private String messageIndexNotOpen() {
        return "Index not open";
    }

    /**
     * Close the {@code Index} object.
     */
    public void close() {
        checkState(open, "Index already closed");
        logger.info("Closing Index");

        writeHeaderToFile();
        writeDataPagesToFile();

        pagedFile.forceAllPages();
        pagedFile.close();

        open = false;
    }

    private void writeHeaderToFile() {
        Page headerPage = pagedFile.getFirstPage();
        pagedFile.markDirty(headerPage);
        header.writeTo(headerPage.getData());
        pagedFile.unpinPage(headerPage);
    }

    private void writeDataPagesToFile() {
        for (IndexNode indexNode : buffer.nodes()) {
            if (indexNode.isOverflow()) {
                throw new IllegalStateException(String.format("Node [%d] overflows", indexNode.getPageNum()));
            }
            touch(indexNode);
            markDirty(indexNode);
            indexNode.writeToPage();
            unpinPage(indexNode);
        }
    }

    private void checkAttrType(Attr attr) {
        try {
            header.attrType.check(attr);
        } catch (IllegalArgumentException e) {
            throw new IndexException(e);
        }
    }

    /**
     * Insert a new entry into the index.
     * <p>
     * An index entry is a key-value pair {@code (attr, rid)}, in which
     * {@code attr} is the attribute value, and {@code rid} is the record ID.
     * <p>
     * This method throws an exception if there is already an {@code attr}
     * key in the index.
     * @param attr the attribute object (as key)
     * @param rid the record identifier object (as value)
     */
    public void insertEntry(Attr attr, RID rid) {
        checkNotNull(attr);
        checkNotNull(rid);
        checkState(open, messageIndexNotOpen());
        checkAttrType(attr);
        logger.debug("Insert entry: attr = {}, rid = {}", attr.toSimplifiedString(), rid);
        bpInsert(attr, rid);
    }

    private void bpInsert(Attr attr, RID rid) {
        header.rootPageNum = bpInsert(header.rootPageNum, attr, rid);
    }

    private int bpInsert(int pageNum, Attr attr, RID rid) {
        if (pageNum == IndexHeader.PAGE_NUM_NOT_EXIST) {
            LeafIndexNode node = createLeafIndexNode(true);
            node.insert(attr, rid);
            unpinPage(node); // TODO Where to unpin this?
            return node.getPageNum();
        }

        IndexNode node = getIndexNode(pageNum);
        if (node.isLeaf()) {
            return bpInsertLeaf((LeafIndexNode) node, attr, rid);
        } else {
            return bpInsertNonLeaf((NonLeafIndexNode) node, attr, rid);
        }
    }

    private int bpInsertLeaf(LeafIndexNode node, Attr attr, RID rid) {
        // Insert first, and split at overflow.
        node.insert(attr, rid);

        // Case: the single root node overflows.
        // The one node must split to three nodes:
        // (1) the origin node, (2) the sibling node, (3) the new root node
        if (node.isRoot() && node.isOverflow()) {
            logger.debug("Overflow on node [{}] (single root node). Split.", node.getPageNum());
            // Split the root node
            SplitResult splitResult = splitLeaf(node);
            LeafIndexNode sibling = (LeafIndexNode) splitResult.sibling;
            Attr upKey = splitResult.upKey;

            // Link two leaf nodes to the new root as parent
            node.indexNodeHeader.isRoot = false;
            NonLeafIndexNode parent = createNonLeafIndexNode(true);
            parent.addFirstTwoChildren(node, sibling, upKey);

            logSplitResult(node, sibling, parent, upKey);
            unpinPage(node);
            unpinPage(sibling);
            unpinPage(parent);
            return parent.getPageNum();
        }

        unpinPage(node);
        return node.getPageNum();
    }

    private int bpInsertNonLeaf(NonLeafIndexNode node, Attr attr, RID rid) {
        int childPageNum = node.findChild(attr);
        bpInsert(childPageNum, attr, rid);
        IndexNode child = getIndexNode(childPageNum);

        // Case: an internal node or a leaf node overflows.
        if (child.isOverflow()) {
            logger.debug("Overflow on node ({}) [{}] (child of node [{}]). Split.",
                    child.isLeaf() ? "leaf node" : "internal node",
                    child.getPageNum(), node.getPageNum());
            // Split the child node
            // The child node can either be leaf or non-leaf (internal node).
            SplitResult splitResult = split(child);
            IndexNode sibling = splitResult.sibling;
            Attr upKey = splitResult.upKey;
            node.addChild(sibling, upKey);
            unpinPage(child);
            unpinPage(sibling);
            logSplitResult(child, sibling, node, upKey);
        }

        // Case: the root node (non-leaf) overflows.
        // Note: the order of two overflow handling if-blocks CANNOT be switched!
        if (node.isOverflow() && node.isRoot()) {
            logger.debug("Overflow on node [{}] (root node). Split.", node.getPageNum());
            // Split the root node
            SplitResult splitResult = splitNonLeaf(node);
            NonLeafIndexNode sibling = (NonLeafIndexNode) splitResult.sibling;
            Attr upKey = splitResult.upKey;

            // Link two nodes to the new root as parent
            node.indexNodeHeader.isRoot = false;
            NonLeafIndexNode parent = createNonLeafIndexNode(true);
            parent.addFirstTwoChildren(node, sibling, upKey);

            logSplitResult(node, sibling, parent, upKey);
            unpinPage(node);
            unpinPage(sibling);
            unpinPage(parent);
            return parent.getPageNum();
        }

        unpinPage(node);
        return node.getPageNum();
    }

    private static void logSplitResult(IndexNode node, IndexNode sibling, IndexNode parent, Attr upKey) {
        logger.debug("Split result: [{}] => [{}] {} [{}], parent = [{}]",
                node.getPageNum(),
                node.getPageNum(),
                upKey.toSimplifiedString(),
                sibling.getPageNum(),
                parent.getPageNum());
    }

    private SplitResult split(IndexNode node) {
        if (node.isLeaf()) {
            return splitLeaf((LeafIndexNode) node);
        } else {
            return splitNonLeaf((NonLeafIndexNode) node);
        }
    }

    private SplitResult splitLeaf(LeafIndexNode node) {
        LeafIndexNode sibling = createLeafIndexNode(false);
        Attr upKey = node.split(sibling);
        return new SplitResult(sibling, upKey);
    }

    private SplitResult splitNonLeaf(NonLeafIndexNode node) {
        NonLeafIndexNode sibling = createNonLeafIndexNode(false);
        Attr upKey = node.split(sibling);
        return new SplitResult(sibling, upKey);
    }

    private LeafIndexNode createLeafIndexNode(boolean isRoot) {
        Page page = pagedFile.allocatePage();
        pagedFile.markDirty(page);
        LeafIndexNode node = IndexNode.createLeaf(page, header, isRoot);
        header.numPages++;
        buffer.add(node);
        logger.debug("Node [{}] created, node type: {}", node.getPageNum(), node.getNodeTypeString());
        return node;
    }

    private NonLeafIndexNode createNonLeafIndexNode(boolean isRoot) {
        Page page = pagedFile.allocatePage();
        pagedFile.markDirty(page);
        NonLeafIndexNode node = IndexNode.createNonLeaf(page, header, isRoot);
        header.numPages++;
        buffer.add(node);
        logger.debug("Node [{}] created, node type: {}", node.getPageNum(), node.getNodeTypeString());
        return node;
    }

    // This method will pin the page to buffer
    private IndexNode getIndexNode(int pageNum) {
        if (buffer.contains(pageNum)) {
            IndexNode indexNode = buffer.get(pageNum);
            touch(indexNode);
            return indexNode;
        }
        Page page = pagedFile.getPage(pageNum);
        IndexNode indexNode = IndexNode.open(page, header);
        buffer.add(indexNode);
        return indexNode;
    }

    /**
     * Delete the entry for the {@code (attr, rid)} pair from the index.
     * @param attr
     * @param rid
     */
    public void deleteEntry(Attr attr, RID rid) {
        checkNotNull(attr);
        checkNotNull(rid);
        checkState(open, messageIndexNotOpen());

    }

    public Scan<RID> scan() {
        checkState(open, messageIndexNotOpen());
        return new IndexScan();
    }

    public Scan<RID> scan(Predicate<Attr> predicate) {
        checkNotNull(predicate);
        checkState(open, messageIndexNotOpen());
        return new IndexScan(predicate);
    }

    private class IndexScan implements Scan<RID> {

        IndexScan() {
            this(null);
        }

        IndexScan(Predicate<Attr> predicate) {

        }

        @Override
        public Optional<RID> next() {
            return Optional.empty();
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }
    }

    private void touch(IndexNode indexNode) {
        pagedFile.getPage(indexNode.getPageNum());
    }

    private void markDirty(IndexNode indexNode) {
        pagedFile.markDirty(indexNode.getPageNum());
    }

    private void unpinPage(IndexNode indexNode) {
        pagedFile.unpinPage(indexNode.getPageNum());
    }

    // For test only
    void check() {
        check0();
    }

    private void check0() {
        Queue<Integer> queue = new ArrayDeque<>();
        if (header.rootPageNum != IndexHeader.PAGE_NUM_NOT_EXIST) {
            queue.add(header.rootPageNum);
        }
        // BFS on B+ tree
        int depth = 0;
        while (!queue.isEmpty()) {
            checkState(!queue.isEmpty());
            IndexNode node0 = getIndexNode(queue.peek());
            boolean isRoot = node0.isRoot();
            boolean isLeaf = node0.isLeaf();
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                int pageNum = queue.remove();
                IndexNode node = getIndexNode(pageNum);
                checkState(node.isRoot() == isRoot, String.format("Node[%d] root = %b", node.getPageNum(), node.isRoot()));
                checkState(node.isLeaf() == isLeaf);
                // Check: there is exactly one root
                if (node.isRoot()) {
                    checkState(node.getPageNum() == header.rootPageNum);
                }
                node.check();
                if (!node.isLeaf()) {
                    NonLeafIndexNode nonLeafIndexNode = (NonLeafIndexNode) node;
                    for (NodePointer pointer : nonLeafIndexNode.pointers) {
                        int childPageNum = pointer.getPageNum();
                        queue.add(childPageNum);
                    }
                }
                unpinPage(node);
            }
            depth++;
        }
    }

    // For debug only
    void dump(boolean verbose) {
        PrintWriter out = new PrintWriter(System.out);

        out.println("=============================");
        out.printf("Index file: %s%n", indexFile.toAbsolutePath().toString());
        out.printf("Number of pages: %d%n", pagedFile.getNumOfPages());

        out.println("-----------------------------");
        header.dump(out);

        for (int i = 1; i < pagedFile.getNumOfPages(); i++) {
            out.println("-----------------------------");
            IndexNode indexNode = getIndexNode(i);
            indexNode.dump(out, verbose);
        }

        out.println("=============================");
        out.flush();
    }
}
