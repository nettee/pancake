package me.nettee.pancake.core.index;

import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;
import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.model.AttrType;
import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.model.Scan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
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
    private final File indexFile;

    private IndexHeader header;
    private IndexNodeBuffer buffer;
    private boolean open;

    private Index(PagedFile pagedFile, File indexFile) {
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
    public static Index create(File dataFile, int indexNo, AttrType attrType) {
        checkNotNull(dataFile);
        checkArgument(dataFile.exists(), "data file does not exist: " + dataFile.getPath());
        checkArgument(indexNo >= 0, "indexNo should be non-negative");
        checkNotNull(attrType);

        logger.info("Creating index {} on data file {}", indexNo, dataFile.getPath());

        File indexFile = joinIndexFile(dataFile, indexNo);
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
    public static void destroy(File dataFile, int indexNo) {
        checkNotNull(dataFile);
        checkArgument(dataFile.exists(), "data file does not exist: " + dataFile.getPath());
        checkArgument(indexNo >= 0, "indexNo should be non-negative");

        logger.info("Destroying index {} on data file {}", indexNo, dataFile.getPath());

        File indexFile = joinIndexFile(dataFile, indexNo);
        checkIndexFileExistance(indexFile, dataFile, indexNo);

        boolean deleted = indexFile.delete();
        checkState(deleted);
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
    public static Index open(File dataFile, int indexNo) {
        checkNotNull(dataFile);
        checkArgument(dataFile.exists(), "data file does not exist: " + dataFile.getPath());
        checkArgument(indexNo >= 0, "indexNo should be non-negative");

        logger.info("Opening index {} on data file {}", indexNo, dataFile.getPath());

        File indexFile = joinIndexFile(dataFile, indexNo);
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

    private static File joinIndexFile(File dataFile, int indexNo) {
        String indexFileName = String.format("%s.%d", dataFile.getName(), indexNo);
        return new File(dataFile.getParentFile(), indexFileName);
    }

    private static void checkIndexFileExistance(File indexFile, File dataFile, int indexNo) {
        if (!indexFile.exists()) {
            String msg = String.format("Cannot find index %d on data file %s",
                    indexNo, dataFile.getAbsolutePath());
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
            indexNode.writeToPage();
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
        checkState(open, "Index not open");
        // TODO check attr type
        bpInsert(attr, rid);
    }

    private void bpInsert(Attr attr, RID rid) {
        header.rootPageNum = bpInsert(header.rootPageNum, attr, rid);
    }

    private int bpInsert(int pageNum, Attr attr, RID rid) {
        if (pageNum == IndexHeader.PAGE_NUM_NOT_EXIST) {
            IndexNode node = createIndexNode(true);
            node.bpInsert(attr, rid);
            unpinPage(node); // TODO Where to unpin this?
            return node.getPageNum();
        }

        IndexNode node = getIndexNode(pageNum);
        if (!node.isFull()) {
            node.bpInsert(attr, rid);
            unpinPage(node);
            return node.getPageNum();
        }

        // TODO
        return pageNum;
    }

    private IndexNode createIndexNode(boolean isLeaf) {
        boolean isRoot = header.rootPageNum == IndexHeader.PAGE_NUM_NOT_EXIST;
        if (isRoot) {
            System.out.println("creating root node...");
        } else {
            System.out.println("creating non-root node...");
        }

        Page page = pagedFile.allocatePage();
        pagedFile.markDirty(page);
        IndexNode indexNode = IndexNode.create(page, header, isRoot, isLeaf);
        header.numPages++;
        buffer.add(indexNode);
        return indexNode;
    }

    private IndexNode getIndexNode(int pageNum) {
        if (buffer.contains(pageNum)) {
            IndexNode indexNode = buffer.get(pageNum);
            // TODO touch(indexNode);
            return indexNode;
        }
        // TODO
        throw new AssertionError();
    }

    /**
     * Delete the entry for the {@code (attr, rid)} pair from the index.
     * @param attr
     * @param rid
     */
    public void deleteEntry(Attr attr, RID rid) {
        checkState(open, "Index not open");
    }

    public Scan<RID> scan() {
        checkState(open, "Index not open");
        return new IndexScan();
    }

    public Scan<RID> scan(Predicate<Attr> predicate) {
        checkState(open, "Index not open");
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

        }
    }

    private void unpinPage(IndexNode indexNode) {
        pagedFile.unpinPage(indexNode.getPage());
    }

    // For debug only.
    String dump() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);

        out.println("=============================");

        out.printf("Index file: %s\n", indexFile.getAbsolutePath());
        out.printf("Number of pages: %d\n", pagedFile.getNumOfPages());

        out.println("-----------------------------");
        out.println("Page[0] - Header page");
        out.printf("Attr type: %s\n", header.attrType.toString());
        out.printf("Key length: %d, pointer length: %d\n", header.keyLength, header.pointerLength);
        out.printf("Branching factor (order of B+ tree): %d\n", header.branchingFactor);
        out.printf("Root pageNum: %d\n", header.rootPageNum);

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
