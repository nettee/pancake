# The Record Management Component RedBase Part 1: RM

Source: <https://web.stanford.edu/class/cs346/2015/redbase-rm.html>

Due Sunday, April 12, 11:59PM

- [Introduction](#intro)
- [Header Files](#header)
- [**RM Interface**](#interface)
- [Return Codes and Error Handling](#codes)
- [Implementation Suggestions](#impl)
- [Documentation](#doc)
- [Grading](#grading)
- [Testing and Submission](#testing)
- [RedBase I/O Efficiency Contest](#contest)

## Introduction

The first part of the RedBase system you will implement is the *Record Management* (*RM*) component. The RM component provides classes and methods for managing files of unordered records. All class names, return codes, constants, etc. in this component should begin with the prefix RM. The RM component is a client to the PF component: your RM methods will make calls to the PF methods we have provided. The PF interface is described in a [separate document](https://web.stanford.edu/class/cs346/2015/redbase-pf.html).

Your RM component will store records in paged files provided by the PF component. To manage file contents conveniently, you will probably want to use the first page of each file as a special header page. This page should contain free space information, as well as whatever other information (related to the file as a whole) you find useful for your implementation. You also must decide exactly how records will be laid out on pages of PF files. Your design task is simplified by the fact that each file will contain a set of records that are all the same size (although record sizes may differ across files). Fixed size records make it easier to manage the records and free space on each page, and fixed size records permit record identifiers within a given file to be a simple combination of page number and record position.

## Header Files

- External interface in `rm.h`
- RID declaration in `rm_rid.h`
- Optional internal header such as `rm_internal.h`
- Shared global declarations in `redbase.h`

The spec says not to add new `#include` statements to `rm.h` or `redbase.h`.

## RM Interface

The RM interface consists of five classes: `RM_Manager`, `RM_FileHandle`, `RM_FileScan`, `RM_Record`, and `RID`, plus `RM_PrintError`.

### `RM_Manager`

```cpp
class RM_Manager {
  public:
       RM_Manager  (PF_Manager &pfm);
       ~RM_Manager ();
    RC CreateFile  (const char *fileName, int recordSize);
    RC DestroyFile (const char *fileName);
    RC OpenFile    (const char *fileName, RM_FileHandle &fileHandle);
    RC CloseFile   (RM_FileHandle &fileHandle);
};
```

`CreateFile` must create a PF file and initialize the header page for a fixed record size.

### `RM_FileHandle`

```cpp
class RM_FileHandle {
  public:
       RM_FileHandle  ();
       ~RM_FileHandle ();
    RC GetRec         (const RID &rid, RM_Record &rec) const;
    RC InsertRec      (const char *pData, RID &rid);
    RC DeleteRec      (const RID &rid);
    RC UpdateRec      (const RM_Record &rec);
    RC ForcePages     (PageNum pageNum = ALL_PAGES) const;
};
```

This is the mutable interface for fetching, inserting, deleting, and updating records.

### `RM_FileScan`

```cpp
class RM_FileScan {
  public:
       RM_FileScan  ();
       ~RM_FileScan ();
    RC OpenScan     (const RM_FileHandle &fileHandle,
                     AttrType      attrType,
                     int           attrLength,
                     int           attrOffset,
                     CompOp        compOp,
                     void          *value,
                     ClientHint    pinHint = NO_HINT);
    RC GetNextRec   (RM_Record &rec);
    RC CloseScan    ();
};
```

The scan can return all records or only those whose attribute at `attrOffset` satisfies the comparison.

### `RM_Record`

```cpp
class RM_Record {
  public:
       RM_Record  ();
       ~RM_Record ();

    RC GetData    (char *&pData) const;
    RC GetRid     (RID &rid) const;
};
```

Records must be materialized as copies, not as direct pointers into buffer pages.

### `RID`

```cpp
class RID {
  public:
       RID        ();
       ~RID       ();
       RID        (PageNum pageNum, SlotNum slotNum);
    RC GetPageNum (PageNum &pageNum) const;
    RC GetSlotNum (SlotNum &slotNum) const;
};
```

RID is the permanent record identity, based on page number and slot number.

## Return Codes and Error Handling

The RM component should follow the PF style:

- positive codes for recoverable conditions/errors,
- negative codes for serious or unrecoverable errors,
- validate parameters before touching files,
- unexpected PF return codes may be propagated upward.

## Implementation Suggestions

The spec recommends:

- a file header page,
- data pages with their own page headers,
- copying file-header info into the open file handle,
- stable RID semantics,
- avoiding linear search for free space,
- maintaining a free-page list,
- using a bitmap for slot occupancy,
- allowing the file to grow arbitrarily.

## File Scans

`RM_FileScan` is intended for higher-level query processing, especially later `Select`, `Delete`, and `Update` functionality.

## Documentation / Testing / Submission

The component requires:

- code comments,
- `rm_DOC` design/testing writeup,
- compatibility with `rm.h`,
- more tests than the starter suite.

The page repeatedly stresses I/O efficiency and warns that early design choices will strongly affect later components.
