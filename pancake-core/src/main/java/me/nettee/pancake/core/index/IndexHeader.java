package me.nettee.pancake.core.index;

import me.nettee.pancake.core.record.AttrType;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class IndexHeader {

    private final String MAGIC = "INX-FILE";

    AttrType attrType;

    void init(AttrType attrType) {
        this.attrType = attrType;
    }

    void readFrom(byte[] src) {
        ByteArrayInputStream bais = new ByteArrayInputStream(src);
        DataInputStream in = new DataInputStream(bais);

        try {
            // Check magic string
            byte[] magic0 = new byte[MAGIC.length()];
            int read = in.read(magic0);
            if (read != MAGIC.length()) {
                throw new IndexException("Magic not match");
            }
            if (!MAGIC.equals(new String(magic0, StandardCharsets.US_ASCII))) {
                throw new IndexException("Magic not match");
            }
            attrType = AttrType.readObject(in);
        } catch (IOException e) {
            throw new IndexException(e);
        }
    }

    void writeTo(byte[] dest) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(baos);
        try {
            os.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
            attrType.writeObject(os);
            byte[] data = baos.toByteArray();
            System.arraycopy(data, 0, dest, 0, data.length);
        } catch (IOException e) {
            throw new IndexException(e);
        }
    }
}
