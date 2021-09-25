package file

import (
	"github.com/pkg/errors"
	"os"
)

func FileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

func FileNotExists(path string) bool {
	return !FileExists(path)
}

func RemoveFile(path string) error {
	err := os.Remove(path)
	if err != nil {
		return errors.Errorf("cannot remove file %s: %s", path, err)
	}
	return nil
}

func RemoveFileIfExists(path string) error {
	if FileExists(path) {
		return RemoveFile(path)
	}
	return nil
}
