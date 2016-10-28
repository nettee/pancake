package me.nettee.pancake.core.record;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.function.Predicate;

import me.nettee.pancake.core.page.Page;

public class RecordPage {

	public static final int HEADER_SIZE = 20;

	private class Header {

		int nextFreePage;
		int recordSize;
		int numberOfRecords;
		int capacity;
		int bitsetSize;

		void fromByteArray(byte[] src) {
			try {
				ByteArrayInputStream bais = new ByteArrayInputStream(src);
				DataInputStream is = new DataInputStream(bais);
				nextFreePage = is.readInt();
				recordSize = is.readInt();
				numberOfRecords = is.readInt();
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
				os.writeInt(numberOfRecords);
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
			sb.append(String.format("number of records: %d", numberOfRecords));
			sb.append("\n");
			sb.append(String.format("page capacity: %d", capacity));
			sb.append("\n");
			sb.append(String.format("bitset size: %d", bitsetSize));
			sb.append("\n");
			return sb.toString();
		}
	}

	private final Page page;

	private Header header;
	private BitSet bitset;
	private Record[] records;
	
	private boolean debug;

	private RecordPage(Page page) {
		this.page = page;
		header = new Header();
	}

	public static RecordPage create(Page page, int recordSize) {
		RecordPage recordPage = new RecordPage(page);
		recordPage.init(recordSize);
		return recordPage;
	}

	public static RecordPage open(Page page) {
		RecordPage recordPage = new RecordPage(page);
		recordPage.load();
		return recordPage;
	}

	private int recordPos(int i) {
		return HEADER_SIZE + header.bitsetSize + i * header.recordSize;
	}
	
	private void init(int recordSize) {
		header.nextFreePage = Metadata.NO_FREE_PAGE;
		header.recordSize = recordSize;
		header.numberOfRecords = 0;
		
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
		int capacity = Page.DATA_SIZE - HEADER_SIZE;
		// workaround "minus one"
		int n = (8 * capacity - 7) / (8 * recordSize + 1) - 1;
		header.capacity = n;
		header.bitsetSize = (int) Math.ceil((double) n / 8);

		bitset = new BitSet(n + 1);
		bitset.set(n + 1, true); // workaround "plus one"
		records = new Record[n];
	}

	private void load() {
		byte[] headerByteArray = Arrays.copyOf(page.getData(), HEADER_SIZE);
		header.fromByteArray(headerByteArray);
		byte[] bitsetByteArray = Arrays.copyOfRange(page.getData(), HEADER_SIZE, header.bitsetSize);
		bitset = BitSet.valueOf(bitsetByteArray);
		int n = header.capacity;
		records = new Record[n];
		for (int i = 0; i < n; i++) {
			if (bitset.get(i)) {
				byte[] data = Arrays.copyOfRange(page.getData(), recordPos(i), recordPos(i) + header.recordSize);
				records[i] = new Record(data);
			}
		}
	}

	private void persist0() {
		byte[] headerBytes = header.toByteArray();
		System.arraycopy(headerBytes, 0, page.getData(), 0, HEADER_SIZE);
		byte[] bitsetBytes = bitset.toByteArray();
		System.arraycopy(bitsetBytes, 0, page.getData(), HEADER_SIZE, bitsetBytes.length);
		for (int i = 0; i < header.capacity; i++) {
			if (bitset.get(i)) {
				System.arraycopy(records[i].data, 0, page.getData(), recordPos(i), header.recordSize);
			}
		}
	}

	public void persist() {
		persist0();
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
		if (bitset.get(slotNum) == false) {
			throw new RecordFileException(String.format("record %d doest not exist", slotNum));
		}
		checkRecordBufferConsistency(slotNum);
	}
	
	private void checkRecordBufferConsistency(int slotNum) {
		boolean existsInBitset = bitset.get(slotNum);
		boolean existsInRecordsArray = records[slotNum] != null;
		if (existsInBitset != existsInRecordsArray) {
			throw new RecordFileException("inconsistent internal state at slot " + slotNum);
		}
	}

	public int insert(byte[] data) {
		int insertedSlotNum = 0;
		// find free slot
		while (bitset.get(insertedSlotNum) == true) {
			insertedSlotNum++;
			if (insertedSlotNum >= header.capacity) {
				throw new RecordFileException("insert error: no free slot left");
			}
		}
		checkRecordBufferConsistency(insertedSlotNum);
		bitset.set(insertedSlotNum);
		records[insertedSlotNum] = new Record(Arrays.copyOf(data, data.length));
		header.numberOfRecords++;
		if (debug) {
			System.out.print(dump());
		}
		return insertedSlotNum;
	}
	
	public byte[] get(int slotNum) {
		checkRecordExistence(slotNum);
		byte[] data = records[slotNum].data; 
		if (debug) {
			System.out.print(dump());
		}
		return data;
	}
	
	public void update(int slotNum, byte[] data) {
		checkRecordExistence(slotNum);
		records[slotNum].data = Arrays.copyOf(data, data.length);
		if (debug) {
			System.out.print(dump());
		}
	}
	
	public void delete(int slotNum) {
		checkRecordExistence(slotNum);
		bitset.set(slotNum, false);
		records[slotNum] = null;
		header.numberOfRecords--;
		if (debug) {
			System.out.print(dump());
		}
	}
	
	public boolean isEmpty() {
		return header.numberOfRecords == 0;
	}
	
	public boolean isFull() {
		return header.numberOfRecords >= header.capacity;
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

	@Deprecated
	public Page getPage() {
		return page;
	}
	
	public int getPageNum() {
		return page.getNum();
	}
	
	public void setDebug(boolean debug) {
		this.debug = debug;
	}
	
	public static class RecordIterator implements Iterator<byte[]> {
		
		private final Predicate<byte[]> pred;

		public RecordIterator(Predicate<byte[]> pred) {
			this.pred = pred;
		}

		@Override
		public boolean hasNext() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public byte[] next() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}

}
