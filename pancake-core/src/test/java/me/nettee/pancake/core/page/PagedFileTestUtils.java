package me.nettee.pancake.core.page;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.google.common.base.Preconditions.checkState;

public class PagedFileTestUtils {

    /**
     * Allocate a random number of pages.
     * @param pagedFile the <tt>PagedFile</tt> object
     * @return the number of allocated pages
     */
    static int allocatePages(PagedFile pagedFile) {
        int N = RandomUtils.nextInt(5, 20);
        return allocatePages(pagedFile, N);
    }

    /**
     * Allocate given number of pages.
     * @param pagedFile the <tt>PagedFile</tt> object
     * @param N the number of pages to allocate
     * @return the number of allocated pages
     */
    static int allocatePages(PagedFile pagedFile, int N) {
        for (int i = 0; i < N; i++) {
            pagedFile.allocatePage();
        }
        return N;
    }

    /**
     * Randomly dispose 3 pages, and return their pageNums.
     * @param pagedFile the <tt>PagedFile</tt> object
     * @param N the number of existing pages
     * @return the pageNums of disposed pages
     */
    static Deque<Integer> disposePages(PagedFile pagedFile, int N) {
        Deque<Integer> disposedPageNums = new LinkedList<>();
        for (int i = 0; i < 3; i++) {
            int pageNum = RandomUtils.nextInt(i * N / 3, (i + 1) * N / 3);
            pagedFile.unpinPage(pageNum);
            pagedFile.disposePage(pageNum);
            disposedPageNums.push(pageNum);
        }
        return disposedPageNums;
    }

    /**
     * Unpin pages[0..N).
     * @param pagedFile the <tt>PagedFile</tt> object
     * @param N the number of pages to unpin
     */
    static void unpinPages(PagedFile pagedFile, int N) {
        Set<Integer> pageNums = new HashSet<>();
        for (int i = 0; i < N; i++) {
            pageNums.add(i);
        }
        pagedFile.unpinPages(pageNums);
    }

    /**
     * Unpin pages with pageNum in <tt>pageNumsToUnpin</tt>.
     * @param pagedFile the <tt>PagedFile</tt> object
     * @param pageNumsToUnpin collection of pageNums
     */
    static void unpinPages(PagedFile pagedFile, Collection<Integer> pageNumsToUnpin) {
        Set<Integer> pageNums = new HashSet<>();
        pageNums.addAll(pageNumsToUnpin);
        pagedFile.unpinPages(pageNums);
    }

    /**
     * Unpin pages[0..N) except pages with pageNum in <tt>excepts</tt>
     * @param pagedFile the <tt>PagedFile</tt> object
     * @param N page range bound
     * @param excepts collection of pageNum not to unpin
     */
    static void unpinPages(PagedFile pagedFile, int N, Collection<Integer> excepts) {
        Set<Integer> pageNums = new HashSet<>();
        for (int i = 0; i < N; i++) {
            if (excepts.contains(i)) {
                continue;
            }
            pageNums.add(i);
        }
        pagedFile.unpinPages(pageNums);
    }

    static String randomString() {
        return randomString(50);
    }

    static String randomString(int length) {
        return RandomStringUtils.randomAlphabetic(length);
    }

    static class TwoStrings {
        String str1;
        String str2;
        int len;
        private TwoStrings(String str1, String str2) {
            checkState(str1.length() == str2.length());
            this.str1 = str1;
            this.str2 = str2;
            len = str1.length();
        }
    }

    static TwoStrings randomTwoStrings() {
        int len = 50;
        String str1 = randomString(len);
        String str2;
        do {
            str2 = randomString(len);
        } while (str2.equals(str1));
        return new TwoStrings(str1, str2);
    }

    /**
     * Write <tt>String</tt> type data to the <tt>page</tt>.
     * @param page the <tt>Page</tt> object
     * @param data the <tt>String</tt> type data
     */
    static void putStringData(Page page, String data) {
        byte[] bytes = data.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, page.data, 0, bytes.length);
    }

    /**
     * Read data from the <tt>page</tt> of type <tt>String</tt>
     * @param page the <tt>Page</tt> object
     * @param length the length of data to read
     * @return the <tt>String</tt> type data read
     */
    static String getStringData(Page page, int length) {
        byte[] bytes = Arrays.copyOfRange(page.data, 0, length);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    /**
     * Fill <tt>N</tt> pages. Fill page <tt>i</tt> with
     * <tt>base + String.valueOf(i)</tt>.
     * @param pagedFile the paged file object
     * @param base base string to fill
     * @param N number of pages
     */
    static void fillPages(PagedFile pagedFile, String base, int N) {
        for (int i = 0; i < N; i++) {
            Page page = pagedFile.getPage(i);
            pagedFile.markDirty(page.num);
            String str = base + i;
            putStringData(page, str);
        }
        pagedFile.forceAllPages();
    }
}
