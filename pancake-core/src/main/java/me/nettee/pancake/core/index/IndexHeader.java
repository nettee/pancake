package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Magic;
import me.nettee.pancake.core.record.AttrType;

import java.io.*;

public class IndexHeader {

    private final Magic MAGIC = new Magic("INX-FILE");

    AttrType attrType;

    void init(AttrType attrType) {
        this.attrType = attrType;
    }

    void readFrom(byte[] src) {
        ByteArrayInputStream bais = new ByteArrayInputStream(src);
        DataInputStream in = new DataInputStream(bais);

        try {
            MAGIC.check(in);
            attrType = AttrType.readObject(in);
        } catch (IOException e) {
            throw new IndexException(e);
        }
    }

    void writeTo(byte[] dest) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(baos);
        try {
            os.write(MAGIC.getBytes());
            attrType.writeObject(os);
            byte[] data = baos.toByteArray();
            System.arraycopy(data, 0, dest, 0, data.length);
        } catch (IOException e) {
            throw new IndexException(e);
        }
    }
}
