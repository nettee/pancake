package page

import (
	"encoding/binary"
	"os"

	"github.com/pkg/errors"
)

type PagedFile interface {
	AllocatePage() (*Page, error)

	// Close the paged file. This will flush all pages from buffer pool to disk before closing the file.
	Close() error
}

type pagedFile struct {
	// The read-write data file
	file *os.File

	// number of pages
	n int
}

// Create a paged file. The file should not already exist.
func CreatePagedFile(path string) (PagedFile, error) {
	// TODO check file not exists
	file, err := os.OpenFile(path, os.O_RDWR|os.O_CREATE, 0664)
	if err != nil {
		return nil, errors.Errorf("failed to create paged file %s: %s", path, err)
	}

	return newPagedFile(file, 0), nil
}

// Open a paged file. The file must have been created using `CreatePagedFile`.
func OpenPagedFile(path string) (PagedFile, error) {
	// TODO check file exists
	file, err := os.OpenFile(path, os.O_RDWR, 0664)
	if err != nil {
		return nil, errors.Errorf("failed to open paged file %s: %s", path, err)
	}

	// load pages
	stat, err := file.Stat()
	if err != nil {
		return nil, err
	}
	fileSize := stat.Size()

	// TODO read page numbers for disposed pages.

	pagedFile := newPagedFile(file, fileSize)
	pagedFile.loadPages()
	return pagedFile, nil
}

func newPagedFile(file *os.File, size int64) *pagedFile {
	if size%pageSize != 0 {
		// warn: file length is not dividable by page size
	}
	return &pagedFile{
		file: file,
		n:    int(size / pageSize),
	}
}

func (f *pagedFile) loadPages() {

}

func (f *pagedFile) Close() error {
	// flush pages
	err := f.file.Close()
	if err != nil {
		return errors.Errorf("failed to close paged file: %s", err)
	}
	return nil
}

func (f *pagedFile) AllocatePage() (*Page, error) {
	pageNum := f.n
	f.n++
	page := newPageWithDefaultBytes(pageNum)
	err := f.writePageToFile(page)
	if err != nil {
		return nil, err
	}
	return page, nil
}

func (f *pagedFile) writePageToFile(page *Page) error {
	_, err := f.file.Seek(int64(page.num*pageSize), 0)
	if err != nil {
		return err
	}
	err = binary.Write(f.file, binary.LittleEndian, page.num)
	if err != nil {
		return err
	}
	_, err = f.file.Write(page.data[:])
	if err != nil {
		return err
	}
	return nil
}
