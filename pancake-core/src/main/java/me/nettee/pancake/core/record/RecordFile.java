package me.nettee.pancake.core.record;

import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
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

	private PagedFile file;
	private Metadata metadata;
	@Deprecated
	private Map<Integer, RecordPage> buffer; // TODO remove buffer
	private boolean debug;

	private RecordFile(PagedFile file) {
		this.file = file;
		metadata = new Metadata();
//		buffer = new TreeMap<>();
	}

	public static RecordFile create(File file, int recordSize) {
		checkNotNull(file);
		checkArgument(recordSize >= 4, "record size less than 4 is currently not supported");

		logger.info("Creating RecordFile {}", file.getPath());

		PagedFile pagedFile = PagedFile.create(file);
		checkState(pagedFile.getNumOfPages() == 0, "created paged file is not empty");

		RecordFile recordFile = new RecordFile(pagedFile);
		recordFile.metadata.init(recordSize);

		Page headerPage = pagedFile.allocatePage();
		pagedFile.markDirty(headerPage);
		recordFile.metadata.writeTo(headerPage.getData());
		pagedFile.unpinPage(headerPage);
		pagedFile.forcePage(headerPage);
		logger.info("Header page (page[{}]) initialized", headerPage.getNum());

		return recordFile;
	}

	public static RecordFile open(File file) {
		checkNotNull(file);

		logger.info("Opening RecordFile {}", file.getPath());

		PagedFile pagedFile = PagedFile.open(file);
		if (pagedFile.getNumOfPages() == 0) {
			throw new RecordFileException("opened paged file is empty");
		}

		RecordFile recordFile = new RecordFile(pagedFile);

		Page headerPage = pagedFile.getFirstPage();
		recordFile.metadata.readFrom(headerPage.getData());
		pagedFile.unpinPage(headerPage);

		return recordFile;
	}

	public void close() {
		logger.info("Closing RecordFile");
//		for (RecordPage recordPage : buffer.values()) {
//			recordPage.persist();
//		}
//		buffer.clear();
		file.forceAllPages();
		file.close();
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
		if (debug) {
			recordPage.setDebug(true);
		}
//		buffer.put(recordPage.getPageNum(), recordPage);
		metadata.numPages++;
		return recordPage;
	}

	private RecordPage getRecordPage(int pageNum) {
		// TODO remove buffer
//		if (buffer.containsKey(pageNum)) {
//			return buffer.get(pageNum);
//		}
		Page page = file.getPage(pageNum);
		RecordPage recordPage = RecordPage.open(page);
//		buffer.put(recordPage.getPageNum(), recordPage);
		return recordPage;
	}

	/**
	 * Insert <tt>data</tt> as a new record in file.
	 * 
	 * @param data
	 *            record data
	 * @return record identifier <tt>RID</tt>
	 */
	public RID insertRecord(byte[] data) {
		// TODO check data length
		RecordPage recordPage = getFreeRecordPage();
		markDirty(recordPage);
		int insertedPageNum = recordPage.getPageNum();
		int insertedSlotNum = recordPage.insert(data);
		metadata.numRecords += 1;
		logger.info("Inserted record[{},{}] <{}>", insertedPageNum, insertedSlotNum,
				new String(data, StandardCharsets.US_ASCII));
		// TODO Should we unpin here?
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
	 * @param rid
	 *            record identification
	 * @return record data
	 */
	public byte[] getRecord(RID rid) {
		RecordPage recordPage = getRecordPage(rid.pageNum);
		byte[] record = recordPage.get(rid.slotNum);
		logger.info("Got record[{},{}] = <{}>", rid.pageNum, rid.slotNum,
				new String(record, StandardCharsets.US_ASCII));
		unpinPage(recordPage);
		return record;
	}

	/**
	 * Update the record identified by <tt>rid</tt>. The existing contents of
	 * the record will be replaced by <tt>data</tt>.
	 * 
	 * @param rid
	 *            record identification
	 * @param data
	 *            replacement
	 */
	public void updateRecord(RID rid, byte[] data) {
		// TODO check data length
		RecordPage recordPage = getRecordPage(rid.pageNum);
		recordPage.update(rid.slotNum, data);
		logger.info("Updated record[{},{}] to <{}>", rid.pageNum, rid.slotNum,
				new String(data, StandardCharsets.US_ASCII));
		unpinPage(recordPage);
	}

	public void deleteRecord(RID rid) {
		RecordPage recordPage = getRecordPage(rid.pageNum);
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
	 * @param predicate
	 *            predicate on record data
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

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

}
