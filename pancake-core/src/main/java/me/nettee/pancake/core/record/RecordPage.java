package me.nettee.pancake.core.record;

import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.page.PagedFile;
import me.nettee.pancake.core.page.Pages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Optional;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class RecordPage {

	private static Logger logger = LoggerFactory.getLogger(RecordPage.class);

	// Note: change the value when the structure of Header changes.
	public static final int HEADER_SIZE = 20;

	private static class Header {

		int nextFreePage;
		int recordSize;
		int numRecords;
		int capacity;
		int bitsetSize;

		void fromByteArray(byte[] src) {
			try {
				ByteArrayInputStream bais = new ByteArrayInputStream(src);
				DataInputStream is = new DataInputStream(bais);
				nextFreePage = is.readInt();
				recordSize = is.readInt();
				numRecords = is.readInt();
				capacity = is.readInt();
				bitsetSize = is.readInt();
			} catch (IOException e) {
				throw new RecordFileException(e);
			}
		}

		byte[] toByteArray() {
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DataOutputStream os = new DataOutputStream(baos);
				os.writeInt(nextFreePage);
				os.writeInt(recordSize);
				os.writeInt(numRecords);
				os.writeInt(capacity);
				os.writeInt(bitsetSize);
				byte[] byteArray = baos.toByteArray();
				if (byteArray.length != HEADER_SIZE) {
					throw new IllegalStateException();
				}
				return byteArray;
			} catch (IOException e) {
				throw new RecordFileException(e);
			}
		}

		String dump() {
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("next free page: %d", nextFreePage));
			sb.append("\n");
			sb.append(String.format("record size: %d", recordSize));
			sb.append("\n");
			sb.append(String.format("number of records: %d", numRecords));
			sb.append("\n");
			sb.append(String.format("page capacity: %d", capacity));
			sb.append("\n");
			sb.append(String.format("bitset size: %d", bitsetSize));
			sb.append("\n");
			return sb.toString();
		}
	}

	private static class Bitset {

		private final BitSet bs;

		private Bitset(BitSet bs) {
			this.bs = bs;
		}

		static Bitset empty(int n) {
			BitSet bs = new BitSet(n + 1);
			bs.set(n + 1, true); // workaround "plus one"
			return new Bitset(bs);
		}

		static Bitset fromByteArray(byte[] bytes) {
			BitSet bs = BitSet.valueOf(bytes);
			return new Bitset(bs);
		}

		byte[] toByteArray() {
			return bs.toByteArray();
		}

		boolean get(int i) {
			return bs.get(i);
		}

		void set(int i) {
			bs.set(i);
		}

		void set(int i, boolean b) {
			bs.set(i, b);
		}

		String dump(int n) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < n; i++) {
				char b = bs.get(i) ? '1' : '0';
				sb.append(b);
				if (i % 10 == 9) {
					sb.append('|');
					sb.append(i+1);
					sb.append('|');
				}
			}
			return sb.toString();
		}

	}

	private final Page page;

	private Header header;
	private Bitset bitset;

	private RecordPage(Page page) {
		this.page = page;
		header = new Header();
	}

	/**
	 * The <tt>page</tt> should be newly created by
	 * {@linkplain PagedFile#allocatePage()}.
	 * @param page
	 * @param recordSize
	 * @return
	 */
	public static RecordPage create(Page page, int recordSize) {
		RecordPage recordPage = new RecordPage(page);
		recordPage.init(recordSize);
		return recordPage;
	}

	/**
	 * The <tt>page</tt> should be a valid record page, retrieved via
	 * {@linkplain PagedFile#getPage(int)}.
	 * @param page
	 * @return
	 */
	public static RecordPage open(Page page) {
		RecordPage recordPage = new RecordPage(page);
		recordPage.load();
		return recordPage;
	}

	private void init(int recordSize) {
		header.nextFreePage = Metadata.NO_FREE_PAGE;
		header.recordSize = recordSize;
		header.numRecords = 0;

		/*
		 * Calculate bitset size:
		 *
		 * Let C = capacity of page, R = length of record, n = number of record.
		 *
		 * Rn + ceil(n/8) <= C;
		 *
		 * Rn + ceil(n/8) <= Rn + (n+7)/8 <= C;
		 *
		 * Thus n <= (8C-7)/(8R+1).
		 *
		 * Bitset size = ceil(n/8) bytes.
		 *
		 * Workaround: Java BitSet class has a bug, its capacity wouldn't grow
		 * if all the bits is 0, and toByteArray() returns empty array. Add one
		 * dummy bit at the end of bitset to fix this problem.
		 */
		int pageCapacity = Page.DATA_SIZE - HEADER_SIZE;
		// workaround "minus one"
		int n = (8 * pageCapacity - 7) / (8 * recordSize + 1) - 1;
		header.capacity = n;
		header.bitsetSize = (int) Math.ceil((double) n / 8);
		logger.debug("header.capacity = {}", header.capacity);
		logger.debug("header.bitsetSize = {}", header.bitsetSize);

		bitset = Bitset.empty(n);

		writeHeaderToPage();
		writeBitsetToPage();
	}

	private void load() {
		readHeaderAndBitsetFromPage();
	}

	public String dump() {
		StringBuilder sb = new StringBuilder();
		sb.append("+----------------------------------------------------------------------------+\n");
		sb.append(String.format("Page %d", page.getNum()));
		sb.append("\n");
		sb.append(header.dump());
		sb.append("Bitset:\n");
		for (int i = 0; i < header.capacity; i++) {
			if (i % 64 == 0) {
				sb.append(String.format("%3d: ", i));
			} else if (i % 8 == 0) {
				sb.append(" ");
			}
			sb.append(bitset.get(i) ? "1" : "0");
			if (i % 64 == 64 - 1) {
				sb.append("\n");
			}
		}
		sb.append("\n");
		sb.append("+----------------------------------------------------------------------------+\n");
		return sb.toString();
	}
	
	private void checkRecordExistence(int slotNum) {
		if (!bitset.get(slotNum)) {
			String msg = String.format("record %d does not exist", slotNum);
			throw new RecordNotExistException(msg);
		}
	}

	private void readHeaderAndBitsetFromPage() {
		byte[] headerByteArray = Arrays.copyOf(page.getData(), HEADER_SIZE);
		header.fromByteArray(headerByteArray);
		byte[] bitsetByteArray = Arrays.copyOfRange(page.getData(),
				HEADER_SIZE,
				HEADER_SIZE + header.bitsetSize);
		bitset = Bitset.fromByteArray(bitsetByteArray);
	}

	void writeHeaderToPage() {
		byte[] headerBytes = header.toByteArray();
		System.arraycopy(headerBytes, 0, page.getData(), 0, HEADER_SIZE);
	}

	void writeBitsetToPage() {
		byte[] bitsetBytes = bitset.toByteArray();
		System.arraycopy(bitsetBytes, 0, page.getData(), HEADER_SIZE, bitsetBytes.length);
	}

	private byte[] readRecordFromPage(int slotNum) {
		return Arrays.copyOfRange(page.getData(), recordPos(slotNum),
				recordPos(slotNum) + header.recordSize);
	}

	private void writeRecordToPage(int slotNum, byte[] data) {
		checkArgument(data.length == header.recordSize);
		System.arraycopy(data, 0, page.getData(), recordPos(slotNum), header.recordSize);
	}

	private int recordPos(int i) {
		return HEADER_SIZE + header.bitsetSize + i * header.recordSize;
	}

	/**
	 * Insert record.
	 * @param data record data
	 * @return slot number of inserted record
	 */
	int insert(byte[] data) {
		int slotNum = 0;
		// Find the first free slot
		while (slotNum < header.capacity && bitset.get(slotNum)) {
			slotNum++;
		}
		checkState(slotNum < header.capacity,
			"No free slot left in record page");
		writeRecordToPage(slotNum, data);
		bitset.set(slotNum);
		header.numRecords++;
		return slotNum;
	}

	/**
	 * Get record.
	 * @param slotNum slot number
	 * @return record
	 */
	byte[] get(int slotNum) {
		checkRecordExistence(slotNum);
		return readRecordFromPage(slotNum);
	}
	
	/**
	 * Update record.
	 * @param slotNum slot number
	 * @param data record
	 */
	void update(int slotNum, byte[] data) {
		checkRecordExistence(slotNum);
		writeRecordToPage(slotNum, data);
	}
	
	/**
	 * Delete record.
	 * @param slotNum slot number
	 */
	void delete(int slotNum) {
		checkRecordExistence(slotNum);
		// Fill the record space with default bytes for ease of debugging.
		writeRecordToPage(slotNum, Pages.makeDefaultBytes(header.recordSize));
		bitset.set(slotNum, false);
		header.numRecords--;
	}
	
	public boolean isEmpty() {
		return header.numRecords == 0;
	}
	
	public boolean isFull() {
		return header.numRecords >= header.capacity;
	}
	
	public int getNextFreePage() {
		return header.nextFreePage;
	}
	
	public void setNextFreePage(int pageNum) {
		header.nextFreePage = pageNum;
	}

	public static short ab2s(byte[] ab) {
		if (ab.length != 2) {
			throw new IllegalArgumentException();
		}
		short s = ByteBuffer.wrap(ab).order(ByteOrder.BIG_ENDIAN).getShort();
		return s;
	}

	public static byte[] s2ab(short s) {
		byte[] ab = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(s).array();
		return ab;
	}

	Page getPage() {
		return page;
	}
	
	public int getPageNum() {
		return page.getNum();
	}

    Scan<byte[]> scan(Predicate<byte[]> predicate) {
	    return new RecordScan(predicate);
    }

    private class RecordScan implements Scan<byte[]> {

	    private final Predicate<byte[]> predicate;
	    private int currentSlotNum;
	    private boolean closed = false;

        public RecordScan(Predicate<byte[]> predicate) {
            this.predicate = predicate;
            currentSlotNum = 0;
        }

        // TODO enable predicate
        // TODO consider deleted records
        @Override
        public Optional<byte[]> next() {
            if (closed) {
                throw new IllegalStateException("Scan is closed");
            }
            while (true) {
                if (currentSlotNum >= header.numRecords) {
                    return Optional.empty();
                }
                byte[] record = get(currentSlotNum);
                currentSlotNum++;
                // TODO predicate can no longer be null
                if (predicate == null || predicate.test(record)) {
                    return Optional.of(record);
                }
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }

}
