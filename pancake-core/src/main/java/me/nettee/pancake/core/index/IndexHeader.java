package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Magic;
import me.nettee.pancake.core.model.AttrType;
import me.nettee.pancake.core.page.Page;

import java.awt.*;
import java.io.*;

public class IndexHeader {

    private final Magic MAGIC = new Magic("INX-FILE");

    AttrType attrType;
    int keyLength;
    int pointerLength;
    int branchingFactor;
    int rootPageNum;

    void init(AttrType attrType) {
        this.attrType = attrType;
        this.keyLength = attrType.getLength();
        this.pointerLength = Pointer.SIZE;
        this.branchingFactor = (Page.DATA_SIZE + keyLength) / (keyLength + pointerLength);
        this.rootPageNum = -1; // No root
    }

    void readFrom(byte[] src) {
        ByteArrayInputStream bais = new ByteArrayInputStream(src);
        DataInputStream is = new DataInputStream(bais);
        try {
            MAGIC.check(is);
            attrType = AttrType.readObject(is);
            keyLength = is.readInt();
            pointerLength = is.readInt();
            branchingFactor = is.readInt();
            rootPageNum = is.readInt();
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
            os.writeInt(keyLength);
            os.writeInt(pointerLength);
            os.writeInt(branchingFactor);
            os.writeInt(rootPageNum);
            byte[] data = baos.toByteArray();
            System.arraycopy(data, 0, dest, 0, data.length);
        } catch (IOException e) {
            throw new IndexException(e);
        }
    }
}
