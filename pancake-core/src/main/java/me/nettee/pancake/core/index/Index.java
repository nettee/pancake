package me.nettee.pancake.core.index;

import me.nettee.pancake.core.page.PagedFile;
import me.nettee.pancake.core.record.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;

public class Index {

    private static Logger logger = LoggerFactory.getLogger(Index.class);

    private PagedFile pagedFile;

    private Index(PagedFile pagedFile) {
        this.pagedFile = pagedFile;
    }

    /**
     * Create an index numbered {@code indexNo} on the data file {@code file}.
     * Callers should ensure that {@code indexNo} is unique and non-negative
     * for each index created on a file. Thus, {@code file.indexNo}
     * identifies a unique index.
     * @param file the data file
     * @param indexNo the index number
     * @return
     */
    public static Index create(File file, int indexNo, AttrType attrType) {
        checkNotNull(file);

        logger.info("Creating Index {}", file.getPath());

        PagedFile pagedFile = PagedFile.create(file);

        Index index = new Index(pagedFile);

        return index;
    }

    public static Index open(File file, int indexNo) {
        checkNotNull(file);

        logger.info("Opening Index {}", file.getPath());

        PagedFile pagedFile = PagedFile.open(file);

        Index index = new Index(pagedFile);

        return index;
    }

    public void close() {
        logger.info("Closing Index");

        pagedFile.forceAllPages();
        pagedFile.close();
    }

    /**
     * Insert new index entry.
     * <p>
     * The index entry is a pair ({@code Attr}, {@code RID})
     * <p>
     * This method throws an exception if there is already an entry for
     * ({@code attr}, {@code rid}) in the index.
     * @param attr the attribute object
     * @param rid the record identifier object
     */
    public void insertEntry(Attr attr, RID rid) {

    }

    public void deleteEntry(Attr attr, RID rid) {

    }

    public Scan<RID> scan() {
        return new IndexScan();
    }

    public Scan<RID> scan(Predicate<Attr> predicate) {
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
}
