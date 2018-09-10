package me.nettee.pancake.core.index;

import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.model.AttrType;
import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.model.StringAttr;
import me.nettee.pancake.core.record.RecordFile;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.*;

public class IndexInsertTest {

    private static final int RECORD_SIZE = 40;

    private static final File DATA_FILE = new File("/tmp/ixb.db");
    private static final int INDEX_NO = 0;
    private static final AttrType ATTR_TYPE = AttrType.string(RECORD_SIZE);

    private Index index;

    @BeforeClass
    public static void setUpBeforeClass() {
        if (!DATA_FILE.exists()) {
            RecordFile recordFile = RecordFile.create(DATA_FILE, RECORD_SIZE);
            recordFile.close();
        }
        File dir = DATA_FILE.getParentFile();
        File[] indexFiles = dir.listFiles(file ->
                file.getName().startsWith(DATA_FILE.getName() + "."));
        for (File indexFile : indexFiles) {
            indexFile.delete();
        }
    }

    @Before
    public void setUp() {
        index = Index.create(DATA_FILE, INDEX_NO, ATTR_TYPE);
    }

    @After
    public void tearDown() {
        index.close();
//        Index.destroy(DATA_FILE, INDEX_NO);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private void insertEntry(int i) {
        Attr attr = new StringAttr(String.format("paranoid-android-size040-abcde04-%07d", i));
        RID rid = new RID(4, i);
        index.insertEntry(attr, rid);
    }

    @Test
    public void test() {
        int branchingFactor = 85;
        int leafCapacity = branchingFactor - 1;
        int halfCapacity = leafCapacity / 2;

        // Phase 1: insert into one node
        for (int i = 0; i < leafCapacity; i++) {
            insertEntry(101 + i);
        }

        // Phase 2: the first split
        insertEntry(201);

        // Phase 3: search and insert
        for (int i = 0; i < halfCapacity - 1; i++) {
            insertEntry(301 + i);
        }

        // Phase 4: more splits
        int base4 = 2000;
        for (int k = 0; k < 3; k++) {
            insertEntry(base4 + 100 * k);
            for (int i = 0; i < halfCapacity - 1; i++) {
                insertEntry(base4 + 100 * k + 1 + i);
            }
        }

        // Phase 5: split middle
        int base5 = 1000;
        for (int k = 5; k > 0; k--) {
            for (int i = 0; i < halfCapacity; i++) {
                insertEntry(base5 + 100 * k + i);
            }
            insertEntry(base5 + 100 * k + 99);
        }

        index.close();
        index = Index.open(DATA_FILE, INDEX_NO);
        System.out.println(index.dump());
    }
}
