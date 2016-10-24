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
		int N;
		if (page.getNum() < metadata.numOfPages - 1) {
			// not the last page
			N = metadata.numOfRecordsInOnePage;
		} else {
			N = metadata.numOfRecords
					- (metadata.numOfPages - metadata.dataPageStartingNum) * metadata.numOfRecordsInOnePage;
		}

		Record[] head = new Record[metadata.numOfRecordsInOnePage];
		Deque<Record> free = new LinkedList<>();

		short freeSlotNum = getFreeSlotNum();
		while (freeSlotNum != (short) 0xffff) {
			byte[] ab = Arrays.copyOfRange(page.getData(), freeSlotNum * metadata.recordSize,
					freeSlotNum * metadata.recordSize + 2);
			short slotNum = ab2s(ab);
			System.out.println("deleted slotNum: " + slotNum);
			freeSlotNum = slotNum;
		}
	}

	private short getFreeSlotNum() {
		byte[] ending = Arrays.copyOfRange(page.getData(), Page.DATA_SIZE - 2, Page.DATA_SIZE);
		short nextSlotNum = ab2s(ending);
		return nextSlotNum;
	}
	
	private static short ab2s(byte[] ab) {
		if (ab.length != 2) {
			throw new IllegalArgumentException();
		}
		short s = ByteBuffer.wrap(ab).order(ByteOrder.BIG_ENDIAN).getShort();
		return s;
	}

}
