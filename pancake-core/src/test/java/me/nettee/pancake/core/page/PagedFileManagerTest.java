package me.nettee.pancake.core.page;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

import org.junit.BeforeClass;
import org.junit.Test;

public class PagedFileManagerTest {

	private static PagedFileManager pagedFileManager;

	@BeforeClass
	public static void setUpBeforeClass() {
		pagedFileManager = PagedFileManager.getInstance();
	}
	
//	@Rule
//	ExpectedException thrown = ExpectedException.none();

	@Test
	public void testCreateFile() {
		/*
		 * If file already exists, FileAlreadyExistsException is expected.
		 * Otherwise, file should be created.
		 */
		File file = new File("/tmp/a.db");
		if (file.exists()) {
			try {
				pagedFileManager.createFile(file);
				fail("FileAlreadyExistsException expected");
			} catch (IOException e) {
				assertTrue(e instanceof FileAlreadyExistsException);
			}
		} else {
			try {
				pagedFileManager.createFile(file);
				assertTrue(file.exists());
			} catch (IOException e) {
				assertFalse(e instanceof FileAlreadyExistsException);
			}
		}
	}

	@Test
	public void testDestroyFile() {
		/*
		 * If file already exists, file should be deleted. Otherwise,
		 * FileNotFoundException is expected.
		 */
		File file = new File("/tmp/a.db");
		if (file.exists()) {
			try {
				pagedFileManager.destroyFile(file);
				assertFalse(file.exists());
			} catch (FileNotFoundException e) {
				fail("FileNotFoundException should not be thrown");
			}
		} else {
			try {
				pagedFileManager.destroyFile(file);
				fail("FileNotFoundException expected");
			} catch (FileNotFoundException e) {
				;
			}
		}

	}

}
