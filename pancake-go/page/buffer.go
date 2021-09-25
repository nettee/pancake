package page

const bufferSize = 40

type bufferedPage struct {
	*Page
	pinned bool
	dirty  bool
}

func newBufferedPage(page *Page) *bufferedPage {
	b := &bufferedPage{
		Page:   page,
		pinned: false,
		dirty:  false,
	}
	return b
}

type buffer map[int32]*bufferedPage

type pageBuffer struct {
	buffer
}

func newPageBuffer() pageBuffer {
	return pageBuffer{
		make(map[int32]*bufferedPage, bufferSize),
	}
}

func (b pageBuffer) put(page *bufferedPage) {
	b.buffer[page.num] = page
}

func (b pageBuffer) get(pageNum int32) (*bufferedPage, bool) {
	page, ok := b.buffer[pageNum]
	return page, ok
}

func (b pageBuffer) remove(pageNum int32) {
	delete(b.buffer, pageNum)
}

func (b pageBuffer) isFull() bool {
	return len(b.buffer) >= bufferSize
}

func (b *pageBuffer) putAndPin(page *Page) {
	// TODO when buffer is full
	bufferedPage := newBufferedPage(page)
	b.put(bufferedPage)
	b.pin(bufferedPage)
}

func (b pageBuffer) pin(page *bufferedPage) {
	page.pinned = true
	// TODO add pinned page
}

func (b pageBuffer) unpin(page *bufferedPage) {
	page.pinned = false
	// TODO remove pinned page
}
