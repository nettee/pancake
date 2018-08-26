package me.nettee.pancake.core.index;

import me.nettee.pancake.core.record.AttrType;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IndexManagerTest {

    private static final AttrType ATTR_TYPE = AttrType.string(8);

    private File dataFile;

    @Before
    public void setUp() throws IOException {
        dataFile = new File("/tmp/ixa.db");

        File dir = dataFile.getParentFile();
        File[] files = dir.listFiles(file -> file.getName().startsWith(dataFile.getName()));
        for (File file : files) {
            file.delete();
        }

        dataFile.createNewFile();
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static int randomIndexNo() {
        return RandomUtils.nextInt(0, 10);
    }

    private static List<Integer> randomIndexNos() {
        List<Integer> nos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
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
    public void testOpen() {
        int indexNo = randomIndexNo();
        Index index = Index.create(dataFile, indexNo, ATTR_TYPE);
        index.close();

        Index index2 = Index.open(dataFile, indexNo);
        index2.close();
    }

    @Test
    public void testOpenWithoutCreate() {
        thrown.expect(Exception.class);
        Index.open(dataFile, 0);
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
