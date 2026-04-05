# The Indexing Component RedBase Part 2: IX

Source: <https://web.stanford.edu/class/cs346/2015/redbase-ix.html>

Due Sunday, April 26, 11:59PM

- [Introduction](#intro)
- [**IX Interface**](#interface)
- [Implementation Details](#impl)
- [Miscellaneous](#misc)

## Introduction

The second part of the RedBase system you will implement is the *Indexing* (*IX*) component. The IX component provides classes and methods for managing persistent indexes over unordered data records stored in paged files. Each data file may have any number of (single-attribute) indexes associated with it. The indexes ultimately will be used to speed up processing of relational selections, joins, and condition-based update and delete operations.

The indexing technique is **B+ trees**. The project explicitly allows some simplifications because a fully correct implementation is complex.

## IX Interface

The interface consists of `IX_Manager`, `IX_IndexHandle`, `IX_IndexScan`, plus `IX_PrintError`.

### `IX_Manager`

```cpp
class IX_Manager {
  public:
       IX_Manager   (PF_Manager &pfm);
       ~IX_Manager  ();
    RC CreateIndex  (const char *fileName,
                     int        indexNo,
                     AttrType   attrType,
                     int        attrLength);
    RC DestroyIndex (const char *fileName,
                     int        indexNo);
    RC OpenIndex    (const char *fileName,
                     int        indexNo,
                     IX_IndexHandle &indexHandle);
    RC CloseIndex   (IX_IndexHandle &indexHandle);
};
```

### `IX_IndexHandle`

```cpp
class IX_IndexHandle {
  public:
       IX_IndexHandle  ();
       ~IX_IndexHandle ();
    RC InsertEntry     (void *pData, const RID &rid);
    RC DeleteEntry     (void *pData, const RID &rid);
    RC ForcePages      ();
};
```

Logical entries are `(*pData, rid)` pairs; the record itself is not stored in the index.

### `IX_IndexScan`

```cpp
class IX_IndexScan {
  public:
       IX_IndexScan  ();
       ~IX_IndexScan ();
    RC OpenScan      (const IX_IndexHandle &indexHandle,
                      CompOp      compOp,
                      void        *value,
                      ClientHint  pinHint = NO_HINT);
    RC GetNextEntry  (RID &rid);
    RC CloseScan     ();
};
```

Scans return RIDs for entries whose indexed values satisfy a comparison condition.

## Implementation Details

Important details from the page:

- each B+ tree node can live in one PF page,
- one straightforward design uses leaf entries pointing to bucket pages of RIDs,
- if one key accumulates too many RIDs for one page, overflow may return an error unless you implement bucket chaining,
- **lazy deletion** is explicitly allowed,
- **tombstones** are allowed with some credit loss,
- full rebalancing deletion earns extra credit,
- recursive algorithms are strongly suggested.

The spec also requires that delete-during-scan use cases work for higher-level query processing.

## Miscellaneous

The page expects:

- comments in code,
- an `ix_DOC` design/testing writeup,
- more tests than the starter suite,
- continued attention to I/O efficiency.
