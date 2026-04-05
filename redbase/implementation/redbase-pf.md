# The Paged File Component RedBase Part 0: PF

Source: <https://web.stanford.edu/class/cs346/2015/redbase-pf.html>

- [Introduction](#intro)
- [The Buffer Pool of Pages](#buffer)
- [Page Numbers](#numbers)
- [Page Deallocation](#dealloc)
- [Scratch Pages](#scratch)
- [Miscellaneous Notes](#misc)
- [**PF Interface**](#interface)
- [Return Codes and Error Handling](#codes)
- [Tracking Buffer Behavior](#tracking)

## Introduction

We will provide code for the "bottom" component of the RedBase system, the *Paged File* (*PF*) component. This component provides facilities for higher-level client components to perform file I/O in terms of pages. In the PF component, methods are provided to create, destroy, open, and close paged files, to scan through the pages of a given file, to read a specific page of a given file, to add and delete pages of a given file, and to obtain and release pages for scratch use. To get started using the PF component, consult the [RedBase Logistics document](https://web.stanford.edu/class/cs346/2015/redbase-logistics.html).

The [C++ interface for the PF component](#interface) is provided below. The name of each class begins with the prefix PF -- you will follow a similar naming convention for your components of the system. Each method in the PF component except constructors and destructors returns an integer code; the same will be true of all of the methods you will write. A return code of 0 indicates normal completion. A nonzero return code indicates that an exception condition or error has occurred. Positive nonzero return codes indicate non-error exception conditions (such as reaching the end of a file) or errors from which the system can recover or exit gracefully (such as trying to close an unopened file). Negative nonzero return codes indicate errors from which the system cannot recover. PF [return codes and error handling](#codes) are described below.

## The Buffer Pool of Pages

Accessing data on a page of a file requires first reading the page into a *buffer pool* in main memory, then manipulating (reading or writing) its data there. While a page is in memory and its data is available for manipulation, the page is said to be "pinned" in the buffer pool. A pinned page remains in the buffer pool until it is explicitly "unpinned." A client unpins a page when it is done manipulating the data on that page. Unpinning a page does not necessarily cause the page to be removed from the buffer -- an unpinned page is kept in memory as long as its space in the buffer pool is not needed.

If the PF component needs to read a new page into memory and there are no free spaces left in the buffer pool, then the PF component will choose an unpinned page to remove from the buffer pool and will reuse its space. The PF component uses a Least-Recently-Used (LRU) page replacement policy. When a page is removed from the buffer pool, it is copied back to the file on disk if and only if the page is marked as "dirty." Dirty pages are not written to disk automatically until they are removed from the buffer. However, a PF client can always send an explicit request to force (i.e., write to disk) the contents of a particular page, or to force all dirty pages of a file, without removing those pages from the buffer.

It is important not to leave pages pinned in memory unnecessarily. The PF component clients that you will implement can be designed so that each operation assumes none of the pages it needs are in the buffer pool: A client fetches the pages it needs, performs the appropriate actions on them, and then unpins them, even if it thinks a certain page may be needed again in the near future. (If the page is used again soon then it will probably still be in the buffer pool anyway.) The PF component does allow the same page to be pinned more than once, without unpinning it in between. In this case, the page won't actually be unpinned until the number of unpin operations matches the number of pin operations. It is very important that each time you fetch and pin a page, you don't forget to unpin it when you're done. If you fail to unpin pages, the buffer pool will slowly fill up until you can no longer fetch any pages at all (at which point the PF component will return a negative code).

## Page Numbers

Pages in a file are identified by *page numbers*, which correspond to their location within the file on disk. When you initially create a file and allocate pages, page numbering will be sequential. However, once pages have been deleted, the numbers of newly allocated pages are not sequential. The PF component reallocates previously allocated pages using a LIFO (stack) algorithm -- that is it reallocates the most recently deleted (and not reallocated) page. A brand new page is never allocated if a previously allocated page is available.

When you scan through a file by calling the `GetFirstPage` and `GetNextPage` methods (described below), you will obtain pages in their numeric order, skipping those pages that were allocated and then deleted, and ending the scan with the largest page number currently valid. Since numeric scan order is guaranteed, and because initial page numbering is sequential, it is possible for clients to implement a policy where the first one or more pages of each file are used for header information.

## Page Deallocation

Although the PF component itself deallocates PF file pages, it doesn't "give back" these pages to the underlying Unix file system, because most Unix systems do not have the capability to collapse files and make use of the empty pages. However, you should write your PF clients under the assumption that file collapsing could occur. That is, your code should not need to change if the PF component were modified to do actual file compression after page disposal.

## Scratch Pages

Most RedBase implementations store and manipulate all of their data on pages associated with files. Occasionally, students may wish to implement more sophisticated and efficient algorithms that require storing and manipulating pages of data temporarily in "scratch" memory. In a realistic database system setting, scratch memory competes with file pages for buffer pool space, and the same constraints are a [requirement](https://web.stanford.edu/class/cs346/2015/redbase-faq.html#memory-use) of the [RedBase I/O Efficiency Contest](https://web.stanford.edu/class/cs346/2015/redbase.html#contest). Therefore, the PF component includes methods for allocating and disposing of scratch pages (memory blocks) in the buffer pool. These blocks reside in the buffer pool and are handled by the buffer manager, but they are not associated with a particular file.

*Most students will not make use of these methods.*

## Miscellaneous Notes

- The number of bytes available for data storage on each page is specified by the constant `PF_PAGE_SIZE = 4092`, defined in the PF component. Please do not change this constant.
- The number of pages in the buffer pool is specified by the constant `PF_BUFFER_SIZE = 40`, also defined by the PF component. Please do not change this constant either.
- The PF component handles all memory management for the buffer pool. Clients need not and should not allocate memory for the data pages in the buffer pool.
- Most students will not need to change the PF interface or code, except perhaps prior to implementing a RedBase extension. However, if you would like to modify the PF component then please contact the TA for help.

## PF Interface

The PF interface consists of three classes: the `PF_Manager` class, the `PF_FileHandle` class, and the `PF_PageHandle` class. In addition, there is a `PF_PrintError` routine for printing messages associated with nonzero PF return codes.

### `PF_Manager` Class

```cpp
class PF_Manager
{
  public:
       PF_Manager    ();                           // Constructor
       ~PF_Manager   ();                           // Destructor
    RC CreateFile    (const char *fileName);       // Create a new file
    RC DestroyFile   (const char *fileName);       // Destroy a file
    RC OpenFile      (const char *fileName, PF_FileHandle &fileHandle);
                                                   // Open a file
    RC CloseFile     (PF_FileHandle &fileHandle);  // Close a file
    RC AllocateBlock (char *&buffer);              // Allocate a new scratch page in buffer
    RC DisposeBlock  (char *buffer);               // Dispose of a scratch page
};
```

#### `RC CreateFile (const char *fileName)`

This method creates a paged file called `fileName`. The file should not already exist.

#### `RC DestroyFile (const char *fileName)`

This method destroys the paged file whose name is `fileName`. The file should exist.

#### `RC OpenFile (const char *fileName, PF_FileHandle &fileHandle)`

This method opens the paged file whose name is `fileName`. The file must already exist and it must have been created using the `CreateFile` method. If the method is successful, the `fileHandle` object whose address is passed as a parameter becomes a "handle" for the open file. The file handle is used to manipulate the pages of the file (see the `PF_FileHandle` class description below). It is a (positive) error if `fileHandle` is already a handle for an open file when it is passed to the `OpenFile` method. It is not an error to open the same file more than once if desired, using a different `fileHandle` object each time. Each call to the `OpenFile` method creates a new "instance" of the open file.

Warning: opening a file more than once for data modification is not prevented by the PF component, but doing so is likely to corrupt the file structure and may crash the PF component. Opening a file more than once for reading is no problem.

#### `RC CloseFile (PF_FileHandle &fileHandle)`

This method closes the open file instance referred to by `fileHandle`. The file must have been opened using the `OpenFile` method. All of the file's pages are flushed from the buffer pool when the file is closed. It is a (positive) error to attempt to close a file when any of its pages are still pinned in the buffer pool.

#### `RC AllocateBlock (char *&buffer)`

This method allocates a "scratch" memory page (block) in the buffer pool and sets `buffer` to point to it. The amount of memory available in the block is `PF_PAGE_SIZE + 4 = 4096` bytes. The scratch page is automatically pinned in the buffer pool.

#### `RC DisposeBlock (char *buffer)`

This method disposes of the scratch page in the buffer pool pointed to by `buffer`, which must have been allocated previously by `PF_Manager::AllocateBlock`. Similar to pinning and unpinning, you must call `PF_Manager::DisposeBlock` for each buffer block obtained by calling `PF_Manager::AllocateBlock`; otherwise you will lose pages in the buffer pool permanently.

### `PF_FileHandle` Class

```cpp
class PF_FileHandle {
  public:
       PF_FileHandle  ();                                  // Default constructor
       ~PF_FileHandle ();                                  // Destructor
       PF_FileHandle  (const PF_FileHandle &fileHandle);   // Copy constructor
       PF_FileHandle& operator= (const PF_FileHandle &fileHandle);
                                                           // Overload =
    RC GetFirstPage   (PF_PageHandle &pageHandle) const;   // Get the first page
    RC GetLastPage    (PF_PageHandle &pageHandle) const;   // Get the last page

    RC GetNextPage    (PageNum current, PF_PageHandle &pageHandle) const;
                                                           // Get the next page
    RC GetPrevPage    (PageNum current, PF_PageHandle &pageHandle) const;
                                                           // Get the previous page
    RC GetThisPage    (PageNum pageNum, PF_PageHandle &pageHandle) const;
                                                           // Get a specific page
    RC AllocatePage   (PF_PageHandle &pageHandle);         // Allocate a new page
    RC DisposePage    (PageNum pageNum);                   // Dispose of a page
    RC MarkDirty      (PageNum pageNum) const;             // Mark a page as dirty
    RC UnpinPage      (PageNum pageNum) const;             // Unpin a page
    RC ForcePages     (PageNum pageNum = ALL_PAGES) const; // Write dirty page(s)
                                                           //   to disk
};
```

The file-handle methods support first/last/next/prev/this-page fetches, page allocation and disposal, dirty marking, explicit unpinning, and flushing dirty pages to disk.

### `PF_PageHandle` Class

```cpp
class PF_PageHandle {
  public:
       PF_PageHandle  ();                          // Default constructor
       ~PF_PageHandle ();                          // Destructor
       PF_PageHandle  (const PF_PageHandle &pageHandle);
                                                   // Copy constructor
       PF_PageHandle& operator= (const PF_PageHandle &pageHandle);
                                                   // Overload =
    RC GetData        (char *&pData) const;        // Set pData to point to
                                                   //   the page contents
    RC GetPageNum     (PageNum &pageNum) const;    // Return the page number
};
```

### `PF_PrintError` Routine

`void PF_PrintError (RC rc)` writes a message associated with a nonzero PF return code to `stderr`.

## Return Codes and Error Handling

Positive return codes include:

```text
PF_EOF
PF_PAGEPINNED
PF_PAGENOTINBUF
PF_PAGEUNPINNED
PF_PAGEFREE
PF_INVALIDPAGE
PF_FILEOPEN
PF_CLOSEDFILE
```

Negative return codes include:

```text
PF_NOMEM
PF_NOBUF
PF_INCOMPLETEREAD
PF_INCOMPLETEWRITE
PF_HDRREAD
PF_HDRWRITE
PF_PAGEINBUF
PF_HASHNOTFOUND
PF_HASHPAGEEXIST
PF_INVALIDNAME
PF_UNIX
```

## Tracking Buffer Behavior

The PF component can track statistics about buffer-manager behavior when compiled with `-DPF_STATS`. The materials emphasize that the total number of read/write page requests is the main number to minimize for the efficiency contest. Statistics can be displayed by calling `PF_Statistics()`.
