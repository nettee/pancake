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
import java.util.Arrays;
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

    public void reopen() {
        index.close();
        index = Index.open(DATA_FILE, INDEX_NO);
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
        reopen();
        index.check();
    }

    // The first time to split to two layers
    @Test
    public void splitSingleRootNode() {
        List<Integer> indices = sampleIndices(501, 800, leafCapacity + 1);
        indices.forEach(this::insertEntry);
        reopen();
        index.check();
    }

    @Test
    public void splitLeafNode() {
        List<Integer> indices = sampleIndices(1001, 1300, 3 * leafCapacity);
        indices.forEach(this::insertEntry);
        reopen();
        index.check();
    }

    @Test
    public void fillRootNode() {
        List<Integer> indices = sequentialIndices(1001, 1085);
        indices.forEach(this::insertEntry);
        reopen();
        index.check();
    }

    // The first time to split to three layers
    @Test
    public void splitRootNode() {
        List<Integer> indices = sequentialIndices(1001, 1086);
        indices.forEach(this::insertEntry);
        reopen();
        index.check();
    }

    @Test
    public void splitInternalNode() {
        List<Integer> indices = sequentialIndices(1001, 1128);
        indices.forEach(this::insertEntry);
        reopen();
        index.check();
    }

    @Ignore
    @Test
    public void insertRandom() {
        List<Integer> indices = sampleIndices(1001, 10000, 300);
        System.out.println("indices = " + indices.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "{", "}")));
        indices.forEach(this::insertEntry);
        reopen();
        index.dump(true);
        index.check();
    }

    @Ignore
    @Test
    public void test0() {
        int[] indicesArray = {4063, 9218, 5967, 6888, 7382, 4848, 9213, 7184, 6034, 4917, 6306, 1707, 2472, 1964, 3085, 2770, 7136, 5176, 2601, 3877, 8541, 8256, 4116, 6960, 3335, 8193, 2199, 8056, 7975, 4511, 5480, 8309, 6149, 1146, 6956, 7343, 2716, 2507, 3367, 2759, 7448, 2291, 6871, 4094, 5125, 2551, 1099, 7818, 7778, 4944, 4220, 1535, 9539, 1566, 6749, 5493, 5494, 6240, 4956, 7564, 2603, 8071, 7669, 8128, 8092, 5454, 3702, 4971, 2102, 1198, 4865, 5013, 4484, 6583, 8032, 5608, 9314, 7300, 9715, 4728, 9079, 4535, 7153, 6273, 3404, 7549, 4794, 5070, 8344, 9024, 6861, 8444, 8768, 7763, 5207, 2681, 9121, 6271, 2183, 5111, 3901, 4933, 1782, 2098, 7115, 9232, 7657, 4587, 8248, 3587, 2298, 2935, 5312, 2147, 9666, 4478, 1055, 7677, 7067, 1247, 2428, 9047, 2341, 7675, 2340, 8282, 9009, 3858, 6865, 5618, 3098, 3023, 2236, 6476, 7517, 1747, 9239, 6594, 4808, 1427, 5511, 3825, 3295, 5889, 5217, 9913, 8317, 1435, 9768, 1281, 2260, 3054, 4257, 4520, 1725, 2001, 8878, 5910, 8122, 7084, 1758, 5768, 2153, 5197, 7389, 8357, 2691, 9522, 7087, 1564, 9480, 1097, 7573, 5620, 2337, 1297, 5579, 8177, 4878, 9248, 4831, 1530, 3337, 1830, 2921, 9469, 3301, 2192, 2006, 6739, 2758, 4308, 9829, 4358, 3857, 7078, 8957, 7512, 9318, 6178, 3958, 3260, 6040, 2300, 6238, 4172, 9395, 4536, 5512, 9902, 3290, 8464, 9853, 7060, 1575, 4497, 6353, 5012, 4489, 2698, 7878, 2967, 3051, 8811, 4459, 2037, 1072, 3155, 5257, 3849, 8324, 6223, 4137, 9581, 2100, 2521, 9560, 7511, 5271, 9257, 8687, 4652, 1385, 5984, 5035, 8866, 4788, 3503, 4918, 5441, 9803, 3627, 1622, 3467, 9188, 8080, 6488, 4618, 4949, 4919, 5018, 4332, 1742, 2878, 7865, 4307, 2026, 1877, 3338, 7228, 6892, 9315, 7235, 1794, 3407, 8685, 4399, 8118, 5000, 2905, 3355, 9603, 5813, 6581, 1974, 3589, 4247, 4440, 7475, 8055, 1724, 2804, 6195, 9448, 3395, 3973, 5267, 8386, 9876, 5752};
        for (int i : indicesArray) {
            insertEntry(i);
        }
        reopen();
        index.dump(true);
        index.check();
    }

}
