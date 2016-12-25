package me.nettee.pancake.core.page;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PagedFileTest {

	private PagedFile pagedFile;

	@BeforeClass
	public static void setUpBeforeClass() throws IOException {

	}

	@Before
	public void setUp() throws IOException {
		File file = new File("/tmp/a.db");
		if (file.exists()) {
			file.delete();
		}
		pagedFile = PagedFile.create(file);
	}

	@After
	public void tearDown() {
		pagedFile.close();
	}

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private int allocatePages() {
		int N = RandomUtils.nextInt(5, 20);
		for (int i = 0; i < N; i++) {
			pagedFile.allocatePage();
		}
		return N;
	}

	private List<Integer> disposePages(int N) {
		List<Integer> list = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			int d = RandomUtils.nextInt(i * N / 3, (i + 1) * N / 3);
			pagedFile.disposePage(d);
			list.add(d);
		}
		return list;
	}

	@Test
	public void testAllocatePage() {
		allocatePages();
	}

	@Test
	public void testDisposePage() {
		int N = allocatePages();
		disposePages(N);
	}
	
	@Test
	public void testReAllocatePage() {
		int N = allocatePages();
		List<Integer> list = disposePages(N);
		// to be done
		for (int n : list) {
			pagedFile.allocatePage();
		}
	}

}
