package me.nettee.pancake.core.record;

import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.*;

/**
 * The first page of record file serves as header page (which stores metadata),
 * and the rest pages serves as data page (which stores records). Each data page
 * also contains some header information. For metadata, see {@link Metadata}.
 * For data page, see {@link RecordPage}.
 * 
 * @see Metadata
 * @see RecordPage
 * 
 * @author nettee
 *
 */
public class RecordFile {

	private static Logger logger = LoggerFactory.getLogger(RecordFile.class);

	private static class RecordPageBuffer {

		private Map<Integer, RecordPage> buf = new HashMap<>();

		void add(RecordPage recordPage) {
			int pageNum = recordPage.getPageNum();
			buf.put(pageNum, recordPage);
			logger.info("Page[{}] added to record page buffer", pageNum);
		}

		boolean contains(int pageNum) {
			return buf.containsKey(pageNum);
		}

		RecordPage get(int pageNum) {
			return buf.get(pageNum);
		}

		Collection<RecordPage> pages() {
			return buf.values();
		}
	}

	private PagedFile pagedFile;
	private Metadata metadata;
	private RecordPageBuffer buffer;

	private RecordFile(PagedFile pagedFile) {
		this.pagedFile = pagedFile;
		metadata = new Metadata();
		buffer = new RecordPageBuffer();
	}

	public static RecordFile create(File file, int recordSize) {
		checkNotNull(file);
		checkArgument(recordSize >= 4,
				"record size less than 4 is currently not supported");

		logger.info("Creating RecordFile {}", file.getPath());

		PagedFile pagedFile = PagedFile.create(file);
		checkState(pagedFile.getNumOfPages() == 0,
				"Created paged file is not empty");
		pagedFile.allocatePage(); // As header page

		RecordFile recordFile = new RecordFile(pagedFile);
		recordFile.metadata.init(recordSize);
		logger.info("Metadata initialized");

		return recordFile;
	}

	public static RecordFile open(File file) {
		checkNotNull(file);

		logger.info("Opening RecordFile {}", file.getPath());

		PagedFile pagedFile = PagedFile.open(file);
		checkState(pagedFile.getNumOfPages() > 0,
				"Opened paged file is empty");

		RecordFile recordFile = new RecordFile(pagedFile);
		Page headerPage = pagedFile.getFirstPage();
		recordFile.metadata.readFrom(headerPage.getData());
		logger.info("Metadata loaded");
		pagedFile.unpinPage(headerPage);

		return recordFile;
	}

	public void close() {
		logger.info("Closing RecordFile");

		writeMetadataToPage();
		writeDataPageHeadersAndBitsetsToPage();

		pagedFile.forceAllPages();
		pagedFile.close();
	}

	private void writeMetadataToPage() {
		Page headerPage = pagedFile.getFirstPage();
		pagedFile.markDirty(headerPage);
		metadata.writeTo(headerPage.getData());
		pagedFile.unpinPage(headerPage);
	}

	private void writeDataPageHeadersAndBitsetsToPage() {
		for (RecordPage recordPage : buffer.pages()) {
			recordPage.writeHeaderToPage();
			recordPage.writeBitsetToPage();
		}
	}

	private RecordPage getOneFreeRecordPage() {
		if (hasFreePages()) {
            return getFirstFreePage();
        } else {
			RecordPage recordPage = createRecordPage();
			insertFreePage(recordPage);
			logger.info("No free pages, created record page[{}]", recordPage.getPageNum());
			return recordPage;
		}

	}

	private RecordPage createRecordPage() {
		Page page = pagedFile.allocatePage();
		pagedFile.markDirty(page);
		RecordPage recordPage = RecordPage.create(page, metadata.recordSize);
		metadata.numPages++;
		buffer.add(recordPage);
		return recordPage;
	}

	private RecordPage getRecordPage(int pageNum) {
		if (buffer.contains(pageNum)) {
			RecordPage recordPage = buffer.get(pageNum);
			touch(recordPage);
			return recordPage;
		}
		Page page = pagedFile.getPage(pageNum);
		RecordPage recordPage = RecordPage.open(page);
		buffer.add(recordPage);
		return recordPage;
	}

	/**
	 * Insert new record in file.
	 * 
	 * @param record the record object
	 * @return record identifier <tt>RID</tt>
	 */
	public RID insertRecord(Record record) {
		checkArgument(record.getLength() == metadata.recordSize);
		RecordPage recordPage = getOneFreeRecordPage();
		markDirty(recordPage);
		int insertedPageNum = recordPage.getPageNum();
		int insertedSlotNum = recordPage.insert(record.getData());
		metadata.numRecords += 1;
		logger.info("Inserted record[{},{}] <{}>",
				insertedPageNum, insertedSlotNum, record.toString());
		unpinPage(recordPage);
		if (recordPage.isFull()) {
			logger.info("Record page[{}] now becomes full", recordPage.getPageNum());
			removeFirstFreePage(recordPage);
		}
		return new RID(insertedPageNum, insertedSlotNum);
	}

	/**
	 * Get the record identified by <tt>rid</tt>.
	 * 
	 * @param rid record identification
	 * @return record
	 * @throws RecordNotExistException if <tt>rid</tt> does not exist
	 */
	public Record getRecord(RID rid) {
		RecordPage recordPage = getRecordPage(rid.pageNum);
		try {
			byte[] data = recordPage.get(rid.slotNum);
			Record record = new Record(data);
			logger.info("Got record[{},{}] = <{}>",
                    rid.pageNum, rid.slotNum, record.toString());
			unpinPage(recordPage);
			return record;
		} catch (RecordNotExistException e) {
			logger.error(e.getMessage());
			unpinPage(recordPage);
			throw e;
		}
	}

	/**
	 * Update the record identified by <tt>rid</tt>. The existing contents of
	 * the record will be replaced by <tt>record</tt>.
	 * 
	 * @param rid record identification
	 * @param record replacement
	 * @throws RecordNotExistException if <tt>rid</tt> does not exist
	 */
	public void updateRecord(RID rid, Record record) {
		checkArgument(record.getLength() == metadata.recordSize);
		RecordPage recordPage = getRecordPage(rid.pageNum);
		try {
			recordPage.update(rid.slotNum, record.getData());
			logger.info("Updated record[{},{}] to <{}>",
                    rid.pageNum, rid.slotNum, record.toString());
			unpinPage(recordPage);
		} catch (RecordNotExistException e) {
			logger.error(e.getMessage());
			unpinPage(recordPage);
			throw e;
		}
	}

	/**
	 * Delete the record identified by <tt>rid</tt>.
	 * @param rid record identification
	 * @throws RecordNotExistException if <tt>rid</tt> does not exist
	 */
	public void deleteRecord(RID rid) {
		logger.debug("Deleting record[{},{}]", rid.pageNum, rid.slotNum);
		RecordPage recordPage = getRecordPage(rid.pageNum);
		try {
			markDirty(recordPage);
			recordPage.delete(rid.slotNum);
			metadata.numRecords -= 1;
			logger.info("Deleted record[{},{}]", rid.pageNum, rid.slotNum);
			unpinPage(recordPage);
			if (recordPage.isEmpty()) {
				logger.info("Record page[{}] now becomes empty", recordPage.getPageNum());
				insertFreePage(recordPage);
			} else {
			    // TODO what if one page is inserted more than once?
                // TODO when this page is not marked as free, mark it as free
			    logger.info("Record page[{}] is now half empty", recordPage.getPageNum());
			    insertFreePage(recordPage);
            }
		} catch (RecordNotExistException e) {
			logger.error(e.getMessage());
			unpinPage(recordPage);
			throw e;
		}
	}

	private void insertFreePage(RecordPage recordPage) {
	    // Insert the number of this page as the header node of linked list.
        if (metadata.firstFreePage == Metadata.NO_FREE_PAGE) {
            metadata.firstFreePage = recordPage.getPageNum();
        } else {
            recordPage.setNextFreePage(metadata.firstFreePage);
            metadata.firstFreePage = recordPage.getPageNum();
        }
    }

    private boolean hasFreePages() {
	    return metadata.firstFreePage != Metadata.NO_FREE_PAGE;
    }

    private RecordPage getFirstFreePage() {
	    return getRecordPage(metadata.firstFreePage);
    }

    private void removeFirstFreePage(RecordPage recordPage) {
	    checkArgument(metadata.firstFreePage == recordPage.getPageNum());
        metadata.firstFreePage = recordPage.getNextFreePage();
    }

	/**
	 * Scan over all the records in this file.
	 *
	 * @return an <tt>Scan</tt> to iterate through records
	 */
	public Scan<Record> scan() {
		return new RecordScan();
	}

	public Scan<Record> scan(Predicate<Record> predicate) {
	    return new RecordScan(predicate);
    }

	private class RecordScan implements Scan<Record> {

	    private final Predicate<byte[]> predicate;
	    private final Iterator<Integer> pageIterator;
	    private RecordPage recordPage;
	    private Scan<byte[]> pageScan;
	    private boolean closed;

	    RecordScan() {
	        this(null);
        }

		RecordScan(Predicate<Record> p) {
	        this.predicate = data -> p == null || p.test(new Record(data));
            logger.debug("dataPageOffset = {}", metadata.dataPageOffset);
            logger.debug("numPages = {}", metadata.numPages);
            Set<Integer> targetPages = new TreeSet<>();
            for (int i = metadata.dataPageOffset; i < metadata.numPages; i++) {
                targetPages.add(i);
            }
            pageIterator = targetPages.iterator();
		}

        @Override
        public Optional<Record> next() {
		    if (closed) {
                throw new IllegalStateException("Scan is closed");
            }
		    // TODO consider deleted records and pages
            if (pageScan != null) {
                // One page is under scanning
                Optional<byte[]> optionalRecord = pageScan.next();
                if (optionalRecord.isPresent()) {
                    return Optional.of(new Record(optionalRecord.get()));
                } else {
                    pageScan.close();
                    pageScan = null;
                    unpinPage(recordPage);
                    recordPage = null;
                }
            }
            if (!pageIterator.hasNext()) {
                // All pages finish scanning.
                return Optional.empty();
            }
            // Start to scan new pages.
			while (pageIterator.hasNext()) {
		        int pageNum = pageIterator.next();
		        recordPage = getRecordPage(pageNum);
		        pageScan = recordPage.scan(predicate);
                Optional<byte[]> optionalRecord = pageScan.next();
                if (optionalRecord.isPresent()) {
                    return Optional.of(new Record(optionalRecord.get()));
                } else {
                    // This page has no records that satisfy the predicate.
                    pageScan.close();
                    pageScan = null;
                    unpinPage(recordPage);
                    recordPage = null;
                }
            }
            return Optional.empty();
        }

        @Override
        public void close() {
            pageScan.close();
            pageScan = null;
            unpinPage(recordPage);
            recordPage = null;
            closed = true;
        }
    }

	/**
	 * Make the page pinned again in paged file.
	 */
	private void touch(RecordPage recordPage) {
		pagedFile.getPage(recordPage.getPageNum());
	}

	private void markDirty(RecordPage recordPage) {
		pagedFile.markDirty(recordPage.getPage());
	}

	private void unpinPage(RecordPage recordPage) {
		pagedFile.unpinPage(recordPage.getPage());
	}

}
