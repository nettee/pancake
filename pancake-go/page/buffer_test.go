package page

import (
	"fmt"
	"testing"
)

func TestLRU(t *testing.T) {
	buffer := newPageBuffer()
	for i := 0; i < 20; i++ {
		page := newPageWithDefaultBytes(i)
		bufferedPage := newBufferedPage(page)
		buffer.put(bufferedPage)
	}

	buffer.lru.summary()

	for _, i := range []int32{1, 12, 10, 6, 9} {
		fmt.Println()
		fmt.Printf("touch %d\n", i)
		buffer.touch(i)
		buffer.lru.summary()
	}
}
