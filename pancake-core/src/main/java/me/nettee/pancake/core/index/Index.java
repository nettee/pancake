package me.nettee.pancake.core.index;

import me.nettee.pancake.core.page.PagedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

public class Index {

    private static Logger logger = LoggerFactory.getLogger(Index.class);

    private PagedFile pagedFile;

    private Index(PagedFile pagedFile) {
        this.pagedFile = pagedFile;
    }

    public static Index create(File file) {
        checkNotNull(file);

        logger.info("Creating Index {}", file.getPath());

        PagedFile pagedFile = PagedFile.create(file);

        Index index = new Index(pagedFile);

        return index;
    }

    public static Index open(File file) {
        checkNotNull(file);

        logger.info("Opening Index {}", file.getPath());

        PagedFile pagedFile = PagedFile.open(file);

        Index index = new Index(pagedFile);

        return index;
    }

    public void close() {
        logger.info("Closing Index");

        pagedFile.forceAllPages();
        pagedFile.close();
    }
}
