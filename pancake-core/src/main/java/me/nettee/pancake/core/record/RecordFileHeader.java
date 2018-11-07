package me.nettee.pancake.core.record;

import me.nettee.pancake.core.model.Magic;
import me.nettee.pancake.core.page.Page;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class RecordFileHeader {
	
	public static final int NO_FREE_PAGE = -1; 
	
	private static final Magic MAGIC = new Magic("REC-FILE");

	int recordSize;
	int dataPageOffset;
	int numRecords;
	int numPages;
	int pageRecordCapacity; // TODO Useless field
	int freeList;

	void init(int recordSize) {
		this.recordSize = recordSize;
		this.dataPageOffset = 1;
		this.numRecords = 0;
		this.numPages = 1;
		this.pageRecordCapacity = (Page.DATA_SIZE - RecordPage.HEADER_SIZE) / recordSize;
		this.freeList = NO_FREE_PAGE;
	}

	void readFrom(byte[] src) {
        ByteArrayInputStream bais = new ByteArrayInputStream(src);
        DataInputStream is = new DataInputStream(bais);
		try {
			MAGIC.check(is);
			recordSize = is.readInt();
			dataPageOffset = is.readInt();
			numRecords = is.readInt();
			numPages = is.readInt();
			pageRecordCapacity = is.readInt();
			freeList = is.readInt();
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}
	
	void writeTo(byte[] dest) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(baos);
		try {
			os.write(MAGIC.getBytes());
			os.writeInt(recordSize);
			os.writeInt(dataPageOffset);
			os.writeInt(numRecords);
			os.writeInt(numPages);
			os.writeInt(pageRecordCapacity);
			os.writeInt(freeList);
			byte[] data = baos.toByteArray();
			System.arraycopy(data, 0, dest, 0, data.length);
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

}
