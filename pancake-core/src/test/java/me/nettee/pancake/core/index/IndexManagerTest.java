package me.nettee.pancake.core.index;

import com.diffplug.common.base.Errors;
import me.nettee.pancake.core.model.AttrType;
import me.nettee.pancake.core.record.RecordFile;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IndexManagerTest {

    private static final int RECORD_SIZE = 8;
    private static final AttrType ATTR_TYPE = AttrType.string(RECORD_SIZE);
    private static final int MAX_INDEX_NO = 10;

    private static Path dataFile = Paths.get("/tmp/ixa.db");

    @BeforeClass
    public static void setUpBeforeClass() {
        if (!Files.exists(dataFile)) {
            RecordFile recordFile = RecordFile.create(dataFile, RECORD_SIZE);
            recordFile.close();
        }
    }

    @Before
    public void setUp() throws IOException {
        Path dir = dataFile.getParent();
        String dataFileName = dataFile.getFileName().toString();
        Files.list(dir)
                .filter(path -> path.getFileName().toString().startsWith(dataFileName + "."))
                .forEach(Errors.rethrow().wrap(Files::delete));
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static int randomIndexNo() {
        return RandomUtils.nextInt(0, MAX_INDEX_NO);
    }

    private static List<Integer> randomIndexNos() {
        List<Integer> nos = new ArrayList<>();
        for (int i = 0; i < MAX_INDEX_NO; i++) {
            nos.add(i);
        }
        Collections.shuffle(nos);
        return nos;
    }

    @Test
    public void testCreate() {
        int indexNo = randomIndexNo();
        Index index = Index.create(dataFile, indexNo, ATTR_TYPE);
        index.close();
    }

    @Test
    public void testDestroy() {
        int indexNo = randomIndexNo();
        Index index = Index.create(dataFile, indexNo, ATTR_TYPE);
        index.close();

        Index.destroy(dataFile, indexNo);
    }

    @Test
    public void testCreateMultiple() {
        List<Integer> indexNos = randomIndexNos();
        for (Integer indexNo : indexNos) {
            Index index = Index.create(dataFile, indexNo, ATTR_TYPE);
            index.close();
        }
    }

    @Test
    public void testCreateDuplicated() {
        int indexNo = randomIndexNo();
        Index index = Index.create(dataFile, indexNo, ATTR_TYPE);
        index.close();

        thrown.expect(Exception.class);
        Index.create(dataFile, indexNo, ATTR_TYPE);
    }

    @Test
    public void testDestroyTwice() {
        int indexNo = randomIndexNo();
        Index index = Index.create(dataFile, indexNo, ATTR_TYPE);
        index.close();

        Index.destroy(dataFile, indexNo);

        thrown.expect(Exception.class);
        Index.destroy(dataFile, indexNo);
    }

    @Test
    public void testCreateDestroyCreate() {
        int indexNo = randomIndexNo();
        Index index = Index.create(dataFile, indexNo, ATTR_TYPE);
        index.close();

        Index.destroy(dataFile, indexNo);

        Index index2 = Index.create(dataFile, indexNo, ATTR_TYPE);
        index2.close();
    }

    @Test
    public void testOpen() {
        int indexNo = randomIndexNo();
        Index index = Index.create(dataFile, indexNo, ATTR_TYPE);
        index.close();

        Index index2 = Index.open(dataFile, indexNo);
        index2.close();
    }

    @Test
    public void testOpenWithoutCreate() {
        int indexNo = randomIndexNo();
        thrown.expect(Exception.class);
        Index.open(dataFile, indexNo);
    }

    @Test
    public void testOpenDestroyed() {
        int indexNo = randomIndexNo();
        Index index = Index.create(dataFile, indexNo, ATTR_TYPE);
        index.close();

        Index.destroy(dataFile, indexNo);

        thrown.expect(Exception.class);
        Index.open(dataFile, indexNo);
    }

    @Test
    public void testOpenOtherIndexNo() {
        List<Integer> indexNos = randomIndexNos();
        int indexNo0 = indexNos.get(0);
        int indexNo1 = indexNos.get(1);

        Index index = Index.create(dataFile, indexNo0, ATTR_TYPE);
        index.close();

        thrown.expect(Exception.class);
        Index.open(dataFile, indexNo1);
    }
}
