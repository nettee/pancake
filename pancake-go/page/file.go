package page

import (
	"encoding/binary"
	"os"
)

type lowLevelFile struct {
	// The read-write data file
	*os.File
}

func (f *lowLevelFile) readPageFromFile(position int) (*Page, error) {
	err := f.seekToPosition(position)
	if err != nil {
		return nil, err
	}
	page := Page{}
	err = binary.Read(f, binary.LittleEndian, &page.num)
	if err != nil {
		return nil, err
	}
	_, err = f.Read(page.data[:])
	if err != nil {
		return nil, err
	}
	return &page, nil
}

func (f *lowLevelFile) writePageToFile(position int, page *Page) error {
	err := f.seekToPosition(position)
	if err != nil {
		return err
	}
	err = binary.Write(f, binary.LittleEndian, page.num)
	if err != nil {
		return err
	}
	_, err = f.Write(page.data[:])
	if err != nil {
		return err
	}
	return nil
}

func (f *lowLevelFile) seekToPosition(position int) error {
	_, err := f.Seek(int64(position*pageSize), 0)
	return err
}
