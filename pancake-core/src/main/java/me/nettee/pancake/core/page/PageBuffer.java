package me.nettee.pancake.core.page;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

// TODO complete buffer implementation for PagedFile
class PageBuffer {

    static final int BUFFER_SIZE = 40;

    private final PagedFile pagedFile;
    private Map<Integer, Page> buf;

    /**
     * Invariant:
     * <ul>
     * <li>The union of <tt>pinnedPages</tt> and <tt>unpinnedPages</tt>
     * makes all buffered pages</li>
     * <li>The intersection of <tt>pinnedPages</tt> and
     * <tt>unpinnedPages</tt> is empty</li>
     * </ul>
     */
    private Set<Integer> pinnedPages, unpinnedPages;

    PageBuffer(PagedFile pagedFile) {
        this.pagedFile = pagedFile;
        buf = new HashMap<>();
        pinnedPages = new HashSet<>();
        unpinnedPages = new LinkedHashSet<>(); // Record the insert order.
    }

    /**
     * Put the <tt>page</tt> into buffer and pin it in buffer. A page is pinned
     * when and only when it is put into buffer.
     * @param page The page to be put and pinned.
     */
    void putAndPin(Page page) {
        if (isFull()) {
            if (hasUnpinnedPages()) {
                int pageNum = getOneUnpinnedPage();
                writeBackAndRemove(pageNum);
                checkState(!isFull());
            } else {
                throw new PagedFileException("buffer pool is already full");
            }
        }
        buf.put(page.num, page);
        pin(page);
    }

    void unpin(int pageNum) {
        Page page = get(pageNum);
        unpin(page);
    }

    private void writeBackAndRemove(int pageNum) {
        Page page = get(pageNum);
        checkNotNull(page);
        checkState(!page.pinned);
        // Note: write back first, then remove
        pagedFile.writeBack(page);
        remove0(pageNum);
    }

    void removeWithoutWriteBack(int pageNum) {
        Page page = get(pageNum);
        checkNotNull(page);
        checkState(!page.pinned);
        remove0(page.num);
    }

    private void remove0(int pageNum) {
        checkState(buf.containsKey(pageNum));
        checkState(!pinnedPages.contains(pageNum));
        checkState(unpinnedPages.contains(pageNum));
        buf.remove(pageNum);
        unpinnedPages.remove(pageNum);
    }


    private void pin(Page page) {
        page.pinned = true;
        pinnedPages.add(page.num);
        checkState(!unpinnedPages.contains(page.num));
    }

    private void unpin(Page page) {
        page.pinned = false;
        pinnedPages.remove(page.num);
        unpinnedPages.add(page.num);
    }

    boolean contains(int pageNum) {
        return buf.containsKey(pageNum);
    }

    private boolean isFull() {
        return buf.size() >= BUFFER_SIZE;
    }

    boolean isPinned(int pageNum) {
        return pinnedPages.contains(pageNum);
    }

    boolean hasPinnedPages() {
        return !pinnedPages.isEmpty();
    }

    private boolean hasUnpinnedPages() {
        return !unpinnedPages.isEmpty();
    }

    // TODO apply LIFO policy
    private int getOneUnpinnedPage() {
        checkState(!unpinnedPages.isEmpty());
        return unpinnedPages.iterator().next();
    }

    // For test only
    Set<Integer> getPinnedPages() {
        return pinnedPages;
    }

    Set<Integer> getUnpinnedPages() {
        return unpinnedPages;
    }

    // TODO what if an unpinned page is got latter?
    Page get(int pageNum) {
        return buf.get(pageNum);
    }
}