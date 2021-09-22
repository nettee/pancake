package page

import "testing"

func Test1(t *testing.T) {
	pagedFile, err := CreatePagedFile("/tmp/a1")
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
