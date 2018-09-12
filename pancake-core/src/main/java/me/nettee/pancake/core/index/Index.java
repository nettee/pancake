package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.model.AttrType;
import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.model.Scan;
import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.*;

/**
 * {@code Index} object is used to handle index files, i.e. insert and delete
 * index entries.
 */
public class Index {

    private static Logger logger = LoggerFactory.getLogger(Index.class);

    private static final String MESSAGE_DATA_FILE_NOT_EXIST = "Data file does not exist: ";
    private static final String MESSAGE_NEGATIVE_INDEX_NO = "IndexNo should be non-negative";
    private static final String MESSAGE_INDEX_NOT_OPEN = "Index not open";

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
        checkArgument(Files.exists(dataFile), MESSAGE_DATA_FILE_NOT_EXIST + dataFile.toString());
        checkArgument(indexNo >= 0, MESSAGE_NEGATIVE_INDEX_NO);
        checkNotNull(attrType);

        logger.info("Creating index {} on data file {}", indexNo, dataFile.toString());

        Path indexFile = joinIndexFile(dataFile, indexNo);
        // Duplicated indexNo will fail on this step.
        PagedFile pagedFile = PagedFile.create(indexFile.toFile());
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
        checkArgument(Files.exists(dataFile), MESSAGE_DATA_FILE_NOT_EXIST + dataFile.toString());
        checkArgument(indexNo >= 0, MESSAGE_NEGATIVE_INDEX_NO);

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
        checkArgument(Files.exists(dataFile), MESSAGE_DATA_FILE_NOT_EXIST + dataFile.toString());
        checkArgument(indexNo >= 0, MESSAGE_NEGATIVE_INDEX_NO);

        logger.info("Opening index {} on data file {}", indexNo, dataFile.toString());

        Path indexFile = joinIndexFile(dataFile, indexNo);
        checkIndexFileExistance(indexFile, dataFile, indexNo);

        PagedFile pagedFile = PagedFile.open(indexFile.toFile());
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
        if (!Files.exists(indexFile)) {
            String msg = String.format("Cannot find index %d on data file %s",
                    indexNo, dataFile.toAbsolutePath().toString());
            throw new IndexException(msg);
        }
    }

    /**
     * Close the {@code Index} object.
     */
    public void close() {
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
            touch(indexNode);
            markDirty(indexNode);
            indexNode.writeToPage();
            unpinPage(indexNode);
        }
    }

    /**
     * Insert a new entry into the index.
     * <p>
     * An index entry is a pair {@code (attr, rid)}, in which
     * {@code attr} is the attribute value, and
     * <p>
     * This method throws an exception if there is already an entry for
     * ({@code attr}, {@code rid}) in the index.
     * @param attr the attribute object
     * @param rid the record identifier object
     */
    public void insertEntry(Attr attr, RID rid) {
        checkNotNull(attr);
        checkNotNull(rid);
        checkState(open, MESSAGE_INDEX_NOT_OPEN);
        // TODO check attr type
        bpInsert(attr, rid);
    }

    private void bpInsert(Attr attr, RID rid) {
        header.rootPageNum = bpInsert(header.rootPageNum, attr, rid);
    }

    private int bpInsert(int pageNum, Attr attr, RID rid) {
        if (pageNum == IndexHeader.PAGE_NUM_NOT_EXIST) {
            LeafIndexNode node = createLeafIndexNode();
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

        if (node.isRoot() && node.isOverflow()) {
            // Split the root node
            LeafIndexNode sibling = splitLeaf(node);
            // Link to leaf nodes to one parent
            NonLeafIndexNode parent = createNonLeafIndexNode();
            parent.addFirstTwoChildren(node, sibling);

            logger.debug("insert and split: {} - {} - {}",
                    node.getPageNum(), parent.getPageNum(), sibling.getPageNum());
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

        if (child.isOverflow()) {
            // Split the child node
            IndexNode sibling = split(child);
            node.addChild(sibling);
        }

        if (node.isRoot() && node.isOverflow()) {
            // TODO
            throw new AssertionError();
        }

        unpinPage(node);
        return node.getPageNum();
    }

    private IndexNode split(IndexNode node) {
        if (node.isLeaf()) {
            return splitLeaf((LeafIndexNode) node);
        } else {
            return splitNonLeaf((NonLeafIndexNode) node);
        }
    }

    private LeafIndexNode splitLeaf(LeafIndexNode node) {
        LeafIndexNode sibling = createLeafIndexNode();
        node.split(sibling);
        return sibling;
    }

    private NonLeafIndexNode splitNonLeaf(NonLeafIndexNode node) {
        NonLeafIndexNode sibling = createNonLeafIndexNode();
        node.split(sibling);
        return sibling;
    }

    private LeafIndexNode createLeafIndexNode() {
        boolean isRoot = header.rootPageNum == IndexHeader.PAGE_NUM_NOT_EXIST;
        Page page = pagedFile.allocatePage();
        pagedFile.markDirty(page);
        LeafIndexNode node = IndexNode.createLeaf(page, header, isRoot);
        header.numPages++;
        buffer.add(node);
        return node;
    }

    private NonLeafIndexNode createNonLeafIndexNode() {
        Page page = pagedFile.allocatePage();
        pagedFile.markDirty(page);
        NonLeafIndexNode node = IndexNode.createNonLeaf(page, header, true);
        header.numPages++;
        buffer.add(node);
        return node;
    }

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
        checkState(open, MESSAGE_INDEX_NOT_OPEN);

    }

    public Scan<RID> scan() {
        checkState(open, MESSAGE_INDEX_NOT_OPEN);
        return new IndexScan();
    }

    public Scan<RID> scan(Predicate<Attr> predicate) {
        checkNotNull(predicate);
        checkState(open, MESSAGE_INDEX_NOT_OPEN);
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

    String dump() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);

        out.println("=============================");

        out.printf("Index file: %s%n", indexFile.toAbsolutePath().toString());
        out.printf("Number of pages: %d%n", pagedFile.getNumOfPages());

        out.println("-----------------------------");
        out.println("Page[0] - Header page");
        out.printf("Attr type: %s%n", header.attrType.toString());
        out.printf("Key length: %d, pointer length: %d%n", header.keyLength, header.pointerLength);
        out.printf("Branching factor (order of B+ tree): %d%n", header.branchingFactor);
        out.printf("Root pageNum: %d%n", header.rootPageNum);

        for (int i = 1; i < pagedFile.getNumOfPages(); i++) {
            out.println("-----------------------------");
            IndexNode indexNode = getIndexNode(i);
            out.print(indexNode.dump());
        }

        out.println("=============================");

        out.close();
        return baos.toString();
    }
}
