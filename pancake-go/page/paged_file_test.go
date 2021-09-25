package page

import (
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/nettee/pancake/file"
)

func Test1(t *testing.T) {
	pagedFile, err := recreatePagedFile("/tmp/a1")
	if err != nil {
		t.Error(err)
	}
	for i := 0; i < 3; i++ {
		_, err = pagedFile.AllocatePage()
		if err != nil {
			t.Error(err)
		}
	}
	err = pagedFile.Close()
	if err != nil {
		t.Error(err)
	}
}

func TestPagedFile_AllocatePage(t *testing.T) {
	pagedFile, err := recreatePagedFile("/tmp/a1")
	if err != nil {
		t.Error(err)
	}
	err = allocatePages(pagedFile, 10)
	if err != nil {
		t.Error(err)
	}
	for i := 0; i < 10; i++ {
		page, err := pagedFile.GetPage(i)
		if err != nil {
			t.Error(err)
		}
		assert.Equal(t, i, int(page.num))
	}

	debugPagedFile(pagedFile)
}

func TestPagedFile_DisposePage(t *testing.T) {
	pagedFile, err := recreatePagedFile("/tmp/a1")
	if err != nil {
		t.Error(err)
	}
	err = allocatePages(pagedFile, 10)
	if err != nil {
		t.Error(err)
	}
	for i := 0; i < 10; i++ {
		page, err := pagedFile.GetPage(i)
		if err != nil {
			t.Error(err)
		}
		assert.Equal(t, i, int(page.num))
	}

	err = pagedFile.DisposePage(1)
	if err != nil {
		t.Error(err)
	}
	err = pagedFile.DisposePage(3)
	if err != nil {
		t.Error(err)
	}
	err = pagedFile.DisposePage(5)
	if err != nil {
		t.Error(err)
	}

	debugPagedFile(pagedFile)
}

func recreatePagedFile(path string) (PagedFile, error) {
	err := file.RemoveFileIfExists(path)
	if err != nil {
		return nil, err
	}
	return CreatePagedFile(path)
}

func allocatePages(pagedFile PagedFile, n int) error {
	for i := 0; i < n; i++ {
		_, err := pagedFile.AllocatePage()
		if err != nil {
			return err
		}
	}
	return nil
}

func debugPagedFile(file PagedFile) {
	fileImpl := file.(*pagedFile)
	fileImpl.summary()
}
