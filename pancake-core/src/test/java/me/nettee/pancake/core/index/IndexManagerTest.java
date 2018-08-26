package me.nettee.pancake.core.index;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;

public class IndexManagerTest {

    private File file;

    @Before
    public void setUp() {
        file = new File("/tmp/ixa.db");
        if (file.exists()) {
            file.delete();
        }
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testCreate() {
        Index index = Index.create(file);
        index.close();
    }

    @Test
    public void testCreate_createTwice() {
        Index index = Index.create(file);
        index.close();

        thrown.expect(Exception.class);
        Index.create(file);
    }

    @Test
    public void testOpen() {
        Index index = Index.create(file);
        index.close();

        Index index2 = Index.open(file);
        index2.close();
    }

    @Test
    public void testOpen_withoutCreate() {
        thrown.expect(Exception.class);
        Index.open(file);
    }
}
