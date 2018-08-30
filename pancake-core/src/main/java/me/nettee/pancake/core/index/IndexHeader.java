package me.nettee.pancake.core.index;

import me.nettee.pancake.core.record.AttrType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class IndexHeader {

    private final String MAGIC = "INX-FILE";

    AttrType attrType;

    void init(AttrType attrType) {
        this.attrType = attrType;
    }

    void readFrom(byte[] src) {

    }

    void writeTo(byte[] dest) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(baos);
        try {
            os.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
            byte[] data = baos.toByteArray();
            System.arraycopy(data, 0, dest, 0, data.length);
        } catch (IOException e) {
            throw new IndexException(e);
        }
    }
}
