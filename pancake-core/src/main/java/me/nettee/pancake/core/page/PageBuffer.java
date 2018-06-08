package me.nettee.pancake.core.page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

class PageBuffer {

    private static Logger logger = LoggerFactory.getLogger(PageBuffer.class);
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
                logger.info("Removed page[{}] from buffer to save space", pageNum);
            } else {
                throw new FullBufferException("Buffer pool is already full");
            }
        }
        checkState(!isFull());
        buf.put(page.num, page);
        pin(page);
        logger.info("Put and pinned page[{}] in buffer", page.num);
    }

    void pinAgainIfNot(Page page) {
        checkState(buf.containsKey(page.num));
        if (!page.pinned) {
            checkState(unpinnedPages.contains(page.num));
            pinAgain(page);
            logger.info("Pineed again page[{}] in buffer", page.num);
        }
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

    private void pinAgain(Page page) {
        page.pinned = true;
        pinnedPages.add(page.num);
        unpinnedPages.remove(page.num);
    }

    // A page can be unpinned twice.
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

    // LRU buffer policy: the oldest unpinned pages will be removed first.
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

    Set<Integer> getAllPages() {
        Set<Integer> allPages = new HashSet<>();
        allPages.addAll(pinnedPages);
        allPages.addAll(unpinnedPages);
        return allPages;
    }

    Page get(int pageNum) {
        return buf.get(pageNum);
    }
}