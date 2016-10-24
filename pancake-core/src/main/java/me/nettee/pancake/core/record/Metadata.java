package me.nettee.pancake.core.record;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Metadata {
	
	private static final String MAGIC = "REC-FILE";

	int recordSize;
	int dataPageStartingNum;
	int numOfRecords;
	int numOfPages;
	int numOfRecordsInOnePage;
	
	void read(byte[] src) {
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(src);
			DataInputStream is = new DataInputStream(bais);
			byte[] magic0 = new byte[MAGIC.length()];
			is.read(magic0);
			if (!MAGIC.equals(new String(magic0, StandardCharsets.US_ASCII))) {
				throw new RecordFileException("magic does not match");
			}
			recordSize = is.readInt();
			dataPageStartingNum = is.readInt();
			numOfRecords = is.readInt();
			numOfPages = is.readInt();
			numOfRecordsInOnePage = is.readInt();
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}
	
	void write(byte[] dest) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream os = new DataOutputStream(baos);
			os.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
			os.writeInt(recordSize);
			os.writeInt(dataPageStartingNum);
			os.writeInt(numOfRecords);
			os.writeInt(numOfPages);
			os.writeInt(numOfRecordsInOnePage);
			byte[] data = baos.toByteArray();
			System.arraycopy(data, 0, dest, 0, data.length);
		} catch (IOException e) {
			throw new RecordFileException(e);
		}
	}
	
	int ridRecordNumber(RID rid) {
		return (rid.pageNum - dataPageStartingNum) * numOfRecordsInOnePage + rid.slotNum;
	}
}
