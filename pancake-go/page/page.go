package page

// The size of page is 4096 bytes.
// The first 4 bytes represents page number (int32), and the rest 4092 bytes stores data.
const (
	pageSize     = 4096
	pageDataSize = 4092
)

const (
	defaultByte byte = 0xee
)

type Page struct {
	num  int32
	data [pageDataSize]byte
}

func newPageWithDefaultBytes(num int) *Page {
	page := Page{num: int32(num)}
	for i := 0; i < pageDataSize; i++ {
		page.data[i] = defaultByte
	}
	return &page
}
