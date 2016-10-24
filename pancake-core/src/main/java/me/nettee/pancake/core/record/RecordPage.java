package me.nettee.pancake.core.record;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;

import me.nettee.pancake.core.page.Page;

public class RecordPage {

	private final Page page;
	private final Metadata metadata;

	RecordPage(Page page, Metadata metadata) {
		this.page = page;
		this.metadata = metadata;
		init();
	}

	private void init() {
		
		Record[] head = new Record[metadata.numOfRecordsInOnePage];
		Deque<Short> free = new LinkedList<>();
		
		int N;
		if (page.getNum() < metadata.numOfPages - 1) {
			// not the last page
			N = metadata.numOfRecordsInOnePage;
		} else {
			N = metadata.numOfRecords
					- (metadata.numOfPages - metadata.dataPageStartingNum) * metadata.numOfRecordsInOnePage;
		}
		
		for (int i = 0; i < N; i++) {
			byte[] data = Arrays.copyOfRange(page.getData(), i * metadata.recordSize, (i + 1) * metadata.recordSize);
			head[i] = new Record(data);
		}
		
		byte[] lastTwoBytes = Arrays.copyOfRange(page.getData(), Page.DATA_SIZE - 2, Page.DATA_SIZE);
		short freeRecordIndex = ab2s(lastTwoBytes);
		
		while (freeRecordIndex != (short) 0xffff) {
			byte[] ab = Arrays.copyOfRange(page.getData(), freeRecordIndex * metadata.recordSize,
					freeRecordIndex * metadata.recordSize + 2);
			free.push(freeRecordIndex);
			head[freeRecordIndex] = null;
			freeRecordIndex = ab2s(ab);
		}
		
		for (int i = 0; i < N; i++) {
			if (head[i] != null) {
				System.out.println("head slot: " + i);
			}
		}
		
		for (short s : free) {
			System.out.println("free slot: " + s);
		}
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

}
