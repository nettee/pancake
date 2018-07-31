package me.nettee.pancake.core.record;

import com.google.common.base.Predicate;
import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

	private RecordPage getFreeRecordPage() {
		if (metadata.firstFreePage == Metadata.NO_FREE_PAGE) {
			RecordPage recordPage = createRecordPage();
			metadata.firstFreePage = recordPage.getPageNum();
			logger.info("Created record page[{}]", recordPage.getPageNum());
			return recordPage;
		} else {
			return getRecordPage(metadata.firstFreePage);
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
	 * Insert <tt>data</tt> as a new record in file.
	 * 
	 * @param data record data
	 * @return record identifier <tt>RID</tt>
	 */
	public RID insertRecord(byte[] data) {
		checkArgument(data.length == metadata.recordSize);
		RecordPage recordPage = getFreeRecordPage();
		markDirty(recordPage);
		int insertedPageNum = recordPage.getPageNum();
		int insertedSlotNum = recordPage.insert(data);
		metadata.numRecords += 1;
		logger.info("Inserted record[{},{}] <{}>", insertedPageNum, insertedSlotNum,
				new String(data, StandardCharsets.US_ASCII));
		unpinPage(recordPage);
		if (recordPage.isFull()) {
			logger.info("Record page[{}] now becomes full", recordPage.getPageNum());
			metadata.firstFreePage = recordPage.getNextFreePage();
		}
		return new RID(insertedPageNum, insertedSlotNum);
	}

	/**
	 * Get the record data identified by <tt>rid</tt>.
	 * 
	 * @param rid record identification
	 * @return record data
	 * @throws RecordNotExistException if <tt>rid</tt> does not exist
	 */
	public byte[] getRecord(RID rid) {
		RecordPage recordPage = getRecordPage(rid.pageNum);
		try {
			byte[] record = recordPage.get(rid.slotNum);
			logger.info("Got record[{},{}] = <{}>", rid.pageNum, rid.slotNum,
					new String(record, StandardCharsets.US_ASCII));
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
	 * the record will be replaced by <tt>data</tt>.
	 * 
	 * @param rid record identification
	 * @param data replacement
	 * @throws RecordNotExistException if <tt>rid</tt> does not exist
	 */
	public void updateRecord(RID rid, byte[] data) {
		checkArgument(data.length == metadata.recordSize);
		RecordPage recordPage = getRecordPage(rid.pageNum);
		try {
			recordPage.update(rid.slotNum, data);
			logger.info("Updated record[{},{}] to <{}>", rid.pageNum, rid.slotNum,
					new String(data, StandardCharsets.US_ASCII));
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
				recordPage.setNextFreePage(metadata.firstFreePage);
				metadata.firstFreePage = recordPage.getPageNum();
			}
		} catch (RecordNotExistException e) {
			logger.error(e.getMessage());
			unpinPage(recordPage);
			throw e;
		}
	}

	/**
	 * Scan over all the records in this file.
	 *
	 * @return an <tt>Scan</tt> to iterate through records
	 */
	public Scan<byte[]> scan() {
		return new RecordScan();
	}

	public Scan<byte[]> scan(Predicate<byte[]> predicate) {
	    return new RecordScan(predicate);
    }

	private class RecordScan implements Scan<byte[]> {

	    private final Predicate<byte[]> predicate;
	    private final Iterator<Integer> pageIterator;
	    private RecordPage recordPage;
	    private Scan<byte[]> pageScan;
	    private boolean closed;

	    RecordScan() {
	        this(null);
        }

		RecordScan(Predicate<byte[]> predicate) {
	        this.predicate = predicate;
            logger.debug("dataPageOffset = {}", metadata.dataPageOffset);
            logger.debug("numPages = {}", metadata.numPages);
            Set<Integer> targetPages = new TreeSet<>();
            for (int i = metadata.dataPageOffset; i < metadata.numPages; i++) {
                targetPages.add(i);
            }
            pageIterator = targetPages.iterator();
		}

        @Override
        public Optional<byte[]> next() {
		    if (closed) {
                throw new IllegalStateException("Scan is closed");
            }
		    // TODO consider deleted records and pages
            if (pageScan != null) {
                // One page is under scanning
                Optional<byte[]> optionalRecord = pageScan.next();
                if (optionalRecord.isPresent()) {
                    return optionalRecord;
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
            // Start to scan a new page.
            // TODO what if no record in this page satisfies predicate?
            int pageNum = pageIterator.next();
            recordPage = getRecordPage(pageNum);
            pageScan = recordPage.scan(predicate);
            Optional<byte[]> optionalRecord = pageScan.next();
            if (!optionalRecord.isPresent()) {
                throw new IllegalStateException();
            }
            return optionalRecord;
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
