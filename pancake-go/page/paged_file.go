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
	GetPage(pageNum int32) (*Page, error)

	// Allocate a new page in the file.
	AllocatePage() (*Page, error)

	// Mark that the page have been or will be modified.
	// A dirty page will be written back to disk when removed from the buffer pool.
	MarkDirty(pageNum int32) error

	// Mark that the page is no longer needed in memory.
	UnpinPage(pageNum int32) error

	// Remove the page specified by pageNum. A page must b unpinned before disposed.
	DisposePage(pageNum int32) error

	// Close the paged file. This will flush all pages from buffer pool to disk before closing the file.
	Close() error
}

type pagedFile struct {
	file lowLevelFile

	// number of pages
	n int

	// page buffer
	buffer pageBuffer
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
		file:   lowLevelFile{file},
		n:      int(size / pageSize),
		buffer: newPageBuffer(),
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

	f.buffer.putAndPin(page)

	return page, nil
}

func (f *pagedFile) MarkDirty(pageNum int32) error {
	if err := f.checkPageNumRange(pageNum); err != nil {
		return err
	}
	page, ok := f.buffer.get(pageNum)
	if !ok {
		return errors.Errorf("cannot mark page %d as dirty which is not in buffer pool", pageNum)
	}
	if !page.pinned {
		return errors.Errorf("cannot mark page %d as dirty, which is not pinned", pageNum)
	}
	page.dirty = true
	return nil
}

func (f *pagedFile) UnpinPage(pageNum int32) error {
	if err := f.checkPageNumRange(pageNum); err != nil {
		return err
	}
	page, ok := f.buffer.get(pageNum)
	if !ok {
		// warning: unpin non-buffered page
	}
	f.buffer.unpin(page)
	return nil
}

func (f *pagedFile) DisposePage(pageNum int32) error {
	if err := f.checkPageNumRange(pageNum); err != nil {
		return err
	}

	// TODO manage dispose page stack

	if page, ok := f.buffer.get(pageNum); ok {
		if page.pinned {
			return errors.Errorf("cannot dispose page %d, page still pinned", pageNum)
		} else {
			f.buffer.remove(pageNum)
		}
	}

	page := newPageWithDefaultBytes(disposedPageNum)
	err := f.file.writePageToFile(int(pageNum), page)
	if err != nil {
		return err
	}
	return nil
}

func (f *pagedFile) GetPage(pageNum int32) (*Page, error) {
	if err := f.checkPageNumRange(pageNum); err != nil {
		return nil, err
	}
	// TODO buffer
	return f.file.readPageFromFile(int(pageNum))
}

func (f *pagedFile) Size() int {
	return f.n
}

func (f *pagedFile) checkPageNumRange(pageNum int32) error {
	if pageNum < 0 || int(pageNum) > f.n {
		return errors.Errorf("page index out of bound: %d", pageNum)
	}
	return nil
}

// For debug only.
func (f *pagedFile) summary() {
	fmt.Printf("pages: %v\n", f.n)
	buffer := f.buffer.buffer
	fmt.Printf("in buffer (%d):\n", len(buffer))
	for i, page := range buffer {
		bytes := page.data[0:32]
		c1 := "U"
		if page.pinned {
			c1 = "P"
		}
		c2 := " "
		if page.dirty {
			c2 = "*"
		}
		fmt.Printf(" %s%s [%2d] %x\n", c1, c2, i, bytes)
	}
	fmt.Printf("in disk (%d):\n", f.n)
	for i := 0; i < f.n; i++ {
		// TODO get page directly from disk
		if page, err := f.GetPage(int32(i)); err == nil {
			bytes := page.data[0:32]
			fmt.Printf("%2d: [%2d] %x\n", i, page.num, bytes)
		}
	}
}
