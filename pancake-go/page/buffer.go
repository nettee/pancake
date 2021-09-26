package page

import (
	"container/list"
	"fmt"
)

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
	lru
}

type lru struct {
	lruList *list.List
	lruMap  map[int32]*list.Element
}

func newPageBuffer() pageBuffer {
	return pageBuffer{
		buffer: make(map[int32]*bufferedPage, bufferSize),
		lru: lru{
			lruList: list.New(),
			lruMap:  make(map[int32]*list.Element, bufferSize),
		},
	}
}

func (b pageBuffer) put(page *bufferedPage) {
	b.buffer[page.num] = page
	b.lru.new(page.num, page)
}

func (b pageBuffer) get(pageNum int32) (*bufferedPage, bool) {
	if page, ok := b.buffer[pageNum]; ok {
		b.lru.touch(pageNum)
		return page, true
	} else {
		return nil, false
	}
}

func (b pageBuffer) remove(pageNum int32) {
	delete(b.buffer, pageNum)
}

// Add an element to the back of LRU list.
func (lru lru) new(pageNum int32, page *bufferedPage) {
	element := lru.lruList.PushBack(page)
	lru.lruMap[pageNum] = element
}

// Move an element to the back of LRU list.
func (lru lru) touch(pageNum int32) {
	if element, ok := lru.lruMap[pageNum]; ok {
		value := element.Value
		lru.lruList.Remove(element)
		newElement := lru.lruList.PushBack(value)
		lru.lruMap[pageNum] = newElement
	}
}

func (lru lru) remove(pageNum int32) (*bufferedPage, bool) {
	if element, ok := lru.lruMap[pageNum]; ok {
		value := lru.lruList.Remove(element)
		return value.(*bufferedPage), true
	} else {
		return nil, false
	}
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

func (lru lru) summary() {
	fmt.Printf("lru list (%d):\n", lru.lruList.Len())
	for e := lru.lruList.Front(); e != nil; e = e.Next() {
		page := e.Value.(*bufferedPage)
		fmt.Printf("%d", page.num)
		if e.Next() != nil {
			fmt.Print(", ")
		}
	}
	fmt.Println()
}
