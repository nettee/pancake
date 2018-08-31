package me.nettee.pancake.core.record;

import me.nettee.pancake.core.model.Magic;
import me.nettee.pancake.core.page.Page;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Metadata {
	
	public static final int NO_FREE_PAGE = -1; 
	
	private static final Magic MAGIC = new Magic("REC-FILE");

	int recordSize;
	int dataPageOffset;
	int numRecords;
	int numPages;
	int pageRecordCapacity;
	int firstFreePage;

	void init(int recordSize) {
		this.recordSize = recordSize;
		this.dataPageOffset = 1;
		this.numRecords = 0;
		this.numPages = 1;
		this.pageRecordCapacity = (Page.DATA_SIZE - RecordPage.HEADER_SIZE) / recordSize;
		this.firstFreePage = Metadata.NO_FREE_PAGE;
	}

	void readFrom(byte[] src) {
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(src);
			DataInputStream is = new DataInputStream(bais);

			MAGIC.check(is);
			recordSize = is.readInt();
			dataPageOffset = is.readInt();
			numRecords = is.readInt();
			numPages = is.readInt();
			pageRecordCapacity = is.readInt();
			firstFreePage = is.readInt();
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}
	
	void writeTo(byte[] dest) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream os = new DataOutputStream(baos);
			os.write(MAGIC.getBytes());
			os.writeInt(recordSize);
			os.writeInt(dataPageOffset);
			os.writeInt(numRecords);
			os.writeInt(numPages);
			os.writeInt(pageRecordCapacity);
			os.writeInt(firstFreePage);
			byte[] data = baos.toByteArray();
			System.arraycopy(data, 0, dest, 0, data.length);
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}

}
