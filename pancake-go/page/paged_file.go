package page

import (
	"fmt"
	"os"

	"github.com/nettee/pancake/file"

	"github.com/pkg/errors"
)

type PagedFile interface {
	// number of pages
	Size() int

	// get page by page number
	GetPage(pageNum int) (*Page, error)

	// Allocate a new page in the file.
	AllocatePage() (*Page, error)

	// Remove the page specified by pageNum. A page must b unpinned before disposed.
	DisposePage(pageNum int) error

	// Close the paged file. This will flush all pages from buffer pool to disk before closing the file.
	Close() error
}

type pagedFile struct {
	file lowLevelFile

	// number of pages
	n int
}

// Create a paged file. The file should not already exist.
func CreatePagedFile(path string) (PagedFile, error) {
	if file.FileExists(path) {
		return nil, errors.Errorf("failed to create paged file %s: file already exists", path)
	}
	file, err := os.OpenFile(path, os.O_RDWR|os.O_CREATE, 0664)
	if err != nil {
		return nil, errors.Errorf("failed to create paged file %s: %s", path, err)
	}

	return newPagedFile(file, 0), nil
}

// Open a paged file. The file must have been created using `CreatePagedFile`.
func OpenPagedFile(path string) (PagedFile, error) {
	if file.FileNotExists(path) {
		return nil, errors.Errorf("failed to open paged file %s: file not exists", path)
	}
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
		file: lowLevelFile{file},
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
	err := f.file.writePageToFile(pageNum, page)
	if err != nil {
		return nil, err
	}
	return page, nil
}

func (f *pagedFile) DisposePage(pageNum int) error {
	// TODO check pageNum range
	// TODO manage dispose page stack
	// TODO buffer

	page := newPageWithDefaultBytes(-1)
	err := f.file.writePageToFile(pageNum, page)
	if err != nil {
		return err
	}
	return nil
}

func (f *pagedFile) GetPage(pageNum int) (*Page, error) {
	// TODO buffer
	return f.file.readPageFromFile(pageNum)
}

func (f *pagedFile) Size() int {
	return f.n
}

// For debug only.
func (f *pagedFile) summary() {
	fmt.Printf("pages: %v\n", f.n)
	for i := 0; i < f.n; i++ {
		if page, err := f.GetPage(i); err == nil {
			bytes := page.data[0:32]
			fmt.Printf("%2d: [%2d]\t%x\n", i, page.num, bytes)
		}
	}
}
