package me.nettee.pancake.core.index;

import com.diffplug.common.base.Errors;
import me.nettee.pancake.core.model.Attr;
import me.nettee.pancake.core.model.AttrType;
import me.nettee.pancake.core.model.RID;
import me.nettee.pancake.core.model.StringAttr;
import me.nettee.pancake.core.page.Page;
import me.nettee.pancake.core.record.RecordFile;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.fail;

public class IndexInsertTest {

    private static final int RECORD_SIZE = 320;

    private static final Path DATA_FILE = Paths.get("/tmp/ixb.db");
    private static final int INDEX_NO = 0;
    private static final AttrType ATTR_TYPE = AttrType.string(RECORD_SIZE);

    private Index index;

    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        if (Files.notExists(DATA_FILE)) {
            RecordFile recordFile = RecordFile.create(DATA_FILE, RECORD_SIZE);
            recordFile.close();
        }
        Path dir = DATA_FILE.getParent();
        String dataFileName = DATA_FILE.getFileName().toString();
        Files.list(dir)
                .filter(path -> path.getFileName().toString().startsWith(dataFileName + "."))
                .forEach(Errors.rethrow().wrap(Files::delete));
    }

    @Before
    public void setUp() {
        index = Index.create(DATA_FILE, INDEX_NO, ATTR_TYPE);
    }

    @After
    public void tearDown() {
        index.close();
        Index.destroy(DATA_FILE, INDEX_NO);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private void insertEntry(int i) {
        String template = ":000ABCDEFGHIJKLMNOPQRSTUVWXYZ" // 30
                + ":030ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + ":060ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + ":090ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + ":120ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + ":150ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + ":180ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + ":210ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + ":240ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + ":270ABCDEFGHIJKLMNOPQRSTUVWXYZ" // 300
                + ":123456789"
                + "---%07d";
        Attr attr = new StringAttr(String.format(template, i));
        RID rid = new RID(4, i); // Fake RID
        index.insertEntry(attr, rid);
    }

    // Giving record size = 320, branching factor = 13
    private static int branchingFactor = (Page.DATA_SIZE - IndexNode.HEADER_SIZE
                + RECORD_SIZE) / (RECORD_SIZE + 8);

    // A single root node can have at most b-1 keys and values
    private static int singleRootCapacity = branchingFactor - 1;
    private static int leafCapacity = branchingFactor - 1;

    private static List<Integer> sampleIndices(int startInclusive, int endExclusive, int n) {
        List<Integer> indices = IntStream.range(startInclusive, endExclusive)
                .boxed()
                .collect(Collectors.toList());
        Collections.shuffle(indices);
        return indices.subList(0, n);
    }

    private static List<Integer> sortedSampleIndices(int startInclusive, int endExclusive, int n) {
        List<Integer> indices = sampleIndices(startInclusive, endExclusive, n);
        Collections.sort(indices);
        return indices;
    }

    private static List<Integer> sequentialIndices(int startInclusive, int endExclusive) {
        return IntStream.range(startInclusive, endExclusive)
                .boxed()
                .collect(Collectors.toList());
    }

    /**
     * Duplicated keys are not allowed.
     */
    @Test
    public void insertDuplicate() {
        List<Integer> indices = sampleIndices(100, 1000, 50);
        indices.forEach(this::insertEntry);
        try {
            insertEntry(indices.get(0));
            fail("IndexException expected");
        } catch (IndexException e) {
            // Expected exception
        }
    }

    @Test
    public void fillSingleRootNode() {
        List<Integer> indices = sampleIndices(501, 800, leafCapacity);
        indices.forEach(this::insertEntry);
//        index.close();
//        index = Index.open(DATA_FILE, INDEX_NO);
//        System.out.println(index.dump(true));
        // TODO assert all keys are ascending
    }

    @Test
    public void splitToTwoLayers() {
        List<Integer> indices = sampleIndices(501, 800, leafCapacity + 1);
        indices.forEach(this::insertEntry);

        index.close();
        index = Index.open(DATA_FILE, INDEX_NO);
        System.out.println(index.dump(true));
    }

    @Test
    public void splitOnLeaf() {
        List<Integer> indices = sampleIndices(1001, 1300, 3 * leafCapacity);
        indices.forEach(this::insertEntry);

        index.close();
        index = Index.open(DATA_FILE, INDEX_NO);
        System.out.println(index.dump(true));
    }

    @Test
    public void fillRootNode() {
        List<Integer> indices = sequentialIndices(1001, 1085);
        indices.forEach(this::insertEntry);
        index.close();
        index = Index.open(DATA_FILE, INDEX_NO);
        System.out.println(index.dump(true));
    }

    // The first time to split non-leaf nodes
    @Test
    public void splitToThreeLayers() {
        List<Integer> indices = sequentialIndices(1001, 1086);
        indices.forEach(this::insertEntry);
        index.close();
        index = Index.open(DATA_FILE, INDEX_NO);
        System.out.println(index.dump(true));
    }

}
