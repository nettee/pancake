package me.nettee.pancake.core.page;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PagedFileBufferTest {

	private static final File file = new File("/tmp/c.db");
	private PagedFile pagedFile;

	@BeforeClass
	public static void setUpBeforeClass() throws IOException {

	}

	@Before
	public void setUp() throws IOException {
		if (file.exists()) {
			file.delete();
		}
		pagedFile = PagedFile.create(file);
	}

	@After
	public void tearDown() {
		pagedFile.close();
	}

	private void reOpen() {
		pagedFile.close();
		pagedFile = PagedFile.open(file);
	}

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private void putStringData(Page page, String data) {
		byte[] bytes = data.getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(bytes, 0, page.data, 0, bytes.length);
	}

	private String getStringData(Page page, int length) {
		byte[] bytes = Arrays.copyOfRange(page.data, 0, length);
		String str = new String(bytes, StandardCharsets.US_ASCII);
		return str;
	}

	@Test
	public void testForcePage() {
		String str = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		{
			Page page = pagedFile.allocatePage();
			pagedFile.markDirty(page.num);
			putStringData(page, str);
			pagedFile.forcePage(page.num);
		}
		reOpen();
		{
			Page page = pagedFile.getPage(0);
			String str2 = getStringData(page, str.length());
			assertEquals(str, str2);
		}
	}

	@Test
	public void testForcePage_noForce() {
		String str1 = "ABCDEFG-HIJKLMN-OPQRST-UVWXYZ";
		String str2 = "OPQRST-UVWXYZ-ABCDEFG-HIJKLMN";
		{
			pagedFile.allocatePage();
		}
		reOpen();
		{
			Page page = pagedFile.getFirstPage();
			pagedFile.markDirty(page.num);
			putStringData(page, str1);
			pagedFile.forcePage(page.num);
		}
		reOpen();
		{
			Page page = pagedFile.getFirstPage();
			putStringData(page, str2);
			// no force page here
		}
		reOpen();
		{
			Page page = pagedFile.getFirstPage();
			String str = getStringData(page, str1.length());
			assertEquals(str1, str);
		}
	}

	// TODO more test cases

}
