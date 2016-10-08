package me.nettee.pancake.core.page;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

/**
 * singleton
 * @author william
 *
 */
public class PagedFileManager {
	
	private static PagedFileManager instance;
	
	private PagedFileManager() {
		
	}
	
	public static PagedFileManager getInstance() {
		if (instance == null) {
			instance = new PagedFileManager();
		}
		return instance;
	}
	
	/**
	 * Creates a paged file. The file should not already exist.
	 * @param file the path to create paged file
	 * @throws IOException 
	 */
	public void createFile(File file) throws IOException {
		if (file.exists()) {
			throw new FileAlreadyExistsException(file.getAbsolutePath());
		}
		file.createNewFile();
	}
	
	/**
	 * Destroys the paged file. The file should exist
	 * @param file the paged file to destroy
	 * @return true if the file is successfully deleted, false otherwise
	 * @throws FileNotFoundException if file not exist
	 */
	public boolean destroyFile(File file) throws FileNotFoundException {
		if (!file.exists()) {
			throw new FileNotFoundException();
		}
		return file.delete();
	}
	
	public PagedFile openFile(File file) throws FileNotFoundException {
		if (!file.exists()) {
			throw new FileNotFoundException();
		}
		return PagedFile.open(file);
	}
	
	public void closeFile(PagedFile pageFile) {
		
	}
	
	public void allocateBlock() {
		
	}
	
	public void disposeBlock() {
		
	}

}
