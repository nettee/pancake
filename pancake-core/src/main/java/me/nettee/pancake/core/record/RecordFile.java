package me.nettee.pancake.core.record;

import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
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

	private PagedFile file;
	private Metadata metadata;

	private RecordFile(PagedFile file) {
		this.file = file;
		metadata = new Metadata();
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

		// Persist
		writeMetadataToPage();
		// TODO persist headers and bitsets in data pages

		file.forceAllPages();
		file.close();
	}

	private void writeMetadataToPage() {
		Page headerPage = file.getFirstPage();
		file.markDirty(headerPage);
		metadata.writeTo(headerPage.getData());
		file.unpinPage(headerPage);
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
		Page page = file.allocatePage();
		file.markDirty(page);
		RecordPage recordPage = RecordPage.create(page, metadata.recordSize);
		// TODO force metadata to disk
		metadata.numPages++;
		return recordPage;
	}

	private RecordPage getRecordPage(int pageNum) {
		// TODO add buffer for header and bitset
		Page page = file.getPage(pageNum);
		RecordPage recordPage = RecordPage.open(page);
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
	 * @return an <tt>Iterator</tt> to iterate through records
	 */
	public Iterator<byte[]> scan() {
		return new RecordIterator();
	}

	/**
	 * Scan over the records in this file that satisfies predicate
	 * <tt>pred</tt>.
	 *
	 * @param predicate predicate on record data
	 * @return an <tt>Iterator</tt> to iterate through records
	 */
	public Iterator<byte[]> scan(Predicate<byte[]> predicate) {
		return new RecordIterator(predicate);
	}

	private class RecordIterator implements Iterator<byte[]> {

		private final Optional<Predicate<byte[]>> predicate;
		
		private Iterator<byte[]> iterator;
		
		RecordIterator() {
			this(null);
		}

		RecordIterator(Predicate<byte[]> predicate) {
			this.predicate = Optional.ofNullable(predicate);
			List<byte[]> allRecords = new ArrayList<>();
			logger.debug("dataPageOffset = {}", metadata.dataPageOffset);
			logger.debug("numOfPages = {}", metadata.numPages);
			for (int pageNum = metadata.dataPageOffset; pageNum < metadata.numPages; pageNum++) {
				RecordPage recordPage = getRecordPage(pageNum);
				logger.debug("page[{}]", pageNum);
				Iterator<byte[]> recordIterator = recordPage.scan(predicate);
				while (recordIterator.hasNext()) {
					byte[] record = recordIterator.next();
					allRecords.add(record);
				}
			}
			iterator = allRecords.iterator();
			// XXX improve efficiency
		}
		
		@Override
		public boolean hasNext() {
			// FIXME consider deleted records
			return iterator.hasNext();
		}

		@Override
		public byte[] next() {
			// FIXME consider deleted records
			return iterator.next();
		}

	}

	private void markDirty(RecordPage recordPage) {
		file.markDirty(recordPage.getPage());
	}

	private void unpinPage(RecordPage recordPage) {
		file.unpinPage(recordPage.getPage());
	}

}
