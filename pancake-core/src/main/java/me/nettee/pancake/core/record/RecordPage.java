package me.nettee.pancake.core.record;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;

import me.nettee.pancake.core.page.Page;

public class RecordPage {

	public static final int HEADER_SIZE = 12;

	private class Header {

		static final int NO_FREE_SLOT = -1;

		int nextFreePage;
		int recordSize;
		short valid;
		short free;

		int numOfRecordsInOnePage; // not persisted
		
		byte[] toByteArray(byte[] dest) {
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DataOutputStream os = new DataOutputStream(baos);
				os.writeInt(nextFreePage);
				os.writeInt(recordSize);
				os.writeShort(valid);
				os.writeShort(free);
				return baos.toByteArray();
			} catch (IOException e) {
				throw new RecordFileException(e);
			}
		}
	}

	private final Page page;
	private Header header;

	// private int N; // number of records in this page

	private RecordPage(Page page) {
		this.page = page;
		header = new Header();
		// if (page.getNum() < metadata.numOfPages - 1) {
		// // not the last page
		// N = metadata.numOfRecordsInOnePage;
		// } else {
		// N = metadata.numOfRecords
		// - (metadata.numOfPages - metadata.dataPageStartingNum) *
		// metadata.numOfRecordsInOnePage;
		// }
		// load();
	}

	public static RecordPage create(Page page, int recordSize) {
		RecordPage recordPage = new RecordPage(page);
		recordPage.header.nextFreePage = Metadata.NO_FREE_PAGE;
		recordPage.header.recordSize = recordSize;
		recordPage.header.valid = 0;
		recordPage.header.free = Header.NO_FREE_SLOT;
		recordPage.header.numOfRecordsInOnePage = (Page.DATA_SIZE - RecordPage.HEADER_SIZE) / recordSize;
		return recordPage;
	}

	public static RecordPage open(Page page) {
		RecordPage recordPage = new RecordPage(page);
		return recordPage;
	}

	private void load() {

		// Record[] head = new Record[metadata.numOfRecordsInOnePage];
		// Deque<Short> free = new LinkedList<>();
		//
		// for (int i = 0; i < N; i++) {
		// byte[] data = Arrays.copyOfRange(page.getData(), i *
		// metadata.recordSize, (i + 1) * metadata.recordSize);
		// head[i] = new Record(data);
		// }
		//
		// byte[] lastTwoBytes = Arrays.copyOfRange(page.getData(),
		// Page.DATA_SIZE - 2, Page.DATA_SIZE);
		// short freeRecordIndex = ab2s(lastTwoBytes);
		//
		// while (freeRecordIndex != (short) 0xffff) {
		// byte[] ab = Arrays.copyOfRange(page.getData(), freeRecordIndex *
		// metadata.recordSize,
		// freeRecordIndex * metadata.recordSize + 2);
		// free.push(freeRecordIndex);
		// head[freeRecordIndex] = null;
		// freeRecordIndex = ab2s(ab);
		// }

		// for (int i = 0; i < N; i++) {
		// if (head[i] != null) {
		// System.out.println("head slot: " + i);
		// }
		// }
		//
		// for (short s : free) {
		// System.out.println("free slot: " + s);
		// }
	}

	public void force() {

	}

	public int insert(byte[] data) {
		System.out.println("calling insert");
		// XXX not yet consider free stack
		if (header.valid >= header.numOfRecordsInOnePage) {
			throw new RecordFileException(
					"no free record space in this page: " + header.valid + ", " + header.numOfRecordsInOnePage);
		}
		System.arraycopy(data, 0, page.getData(), header.valid * header.recordSize, header.recordSize);
		int insertedSlotNum = header.valid;
		header.valid++;
		return insertedSlotNum;
	}

	private static short ab2s(byte[] ab) {
		if (ab.length != 2) {
			throw new IllegalArgumentException();
		}
		short s = ByteBuffer.wrap(ab).order(ByteOrder.BIG_ENDIAN).getShort();
		return s;
	}

	private static byte[] s2ab(short s) {
		byte[] ab = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(s).array();
		return ab;
	}

	public Page getPage() {
		return page;
	}

}
