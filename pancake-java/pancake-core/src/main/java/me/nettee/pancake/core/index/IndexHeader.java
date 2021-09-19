package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Magic;
import me.nettee.pancake.core.model.AttrType;
import me.nettee.pancake.core.page.Page;

import java.io.*;

class IndexHeader {

    static final int PAGE_NUM_NOT_EXIST = -1;

    private static final Magic MAGIC = new Magic("INX-FILE");

    AttrType attrType;
    int keyLength;
    int pointerLength;
    int branchingFactor;
    int numPages; // This field is not currently serialized
    int rootPageNum;

    void init(AttrType attrType) {
        this.attrType = attrType;
        this.keyLength = attrType.getLength();
        this.pointerLength = NodePointer.SIZE;
        this.branchingFactor = (Page.DATA_SIZE - IndexNode.HEADER_SIZE
                + keyLength) / (keyLength + pointerLength);
        this.numPages = 1; // TODO Is this field necessary?
        this.rootPageNum = PAGE_NUM_NOT_EXIST;
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

    // For debug only
    void dump(PrintWriter out) {
        out.println("Page[0] - Header page");
        out.printf("Attr type: %s%n", attrType.toString());
        out.printf("Key length: %d, pointer length: %d%n", keyLength, pointerLength);
        out.printf("Branching factor (order of B+ tree): %d%n", branchingFactor);
        out.printf("Root pageNum: %d%n", rootPageNum);
    }
}
