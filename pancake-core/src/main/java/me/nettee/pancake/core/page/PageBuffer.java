package me.nettee.pancake.core.page;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

// TODO complete buffer implementation for PagedFile
class PageBuffer {

    private static final int BUFFER_SIZE = 40;

    private Map<Integer, Page> buf;

    /**
     * Invariant:
     * <ul>
     * <li>The union of <tt>pinnedPages</tt> and <tt>unpinnedPages</tt>
     * makes all buffered pages</li>
     * <li>The intersection of <tt>pinnedPages</tt> and
     * <tt>unpinnedPages</tt> is empty</li>
     * </ul>
     *
     */
    private Set<Integer> pinnedPages, unpinnedPages;

    PageBuffer() {
        buf = new HashMap<>();
        pinnedPages = new HashSet<>();
        unpinnedPages = new HashSet<>();
    }

    /**
     * Put the <tt>page</tt> into buffer and pin it in buffer. A page is
     * pinned when and only when it is put into buffer.
     * @param page The page to be put and pinned.
     */
    void putAndPin(Page page) {
        checkState(!isFull(), "buffer pool is already full");
        buf.put(page.num, page);
        pin(page);
    }

    void unpin(int pageNum) {
        Page page = get(pageNum);
        unpin(page);
    }

    private void pin(Page page) {
        page.pinned = true;
        pinnedPages.add(page.num);
        unpinnedPages.remove(page.num);
        // TODO should check page.num not contained in unpinnedPages
    }

    private void unpin(Page page) {
        page.pinned = false;
        pinnedPages.remove(page.num);
        unpinnedPages.add(page.num);
    }

    boolean contains(int pageNum) {
        return buf.containsKey(pageNum);
    }

    boolean isFull() {
        return buf.size() >= BUFFER_SIZE;
    }

    boolean hasPinnedPages() {
        return !pinnedPages.isEmpty();
    }

    // For test only
    public Set<Integer> getPinnedPages() {
        return pinnedPages;
    }

    Page get(int pageNum) {
        return buf.get(pageNum);
    }
}