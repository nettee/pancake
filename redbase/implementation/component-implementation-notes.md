# RedBase Component Specs (Implementation-Oriented Notes)

Sources:

- PF: <https://web.stanford.edu/class/cs346/2015/redbase-pf.html>
- RM: <https://web.stanford.edu/class/cs346/2015/redbase-rm.html>
- IX: <https://web.stanford.edu/class/cs346/2015/redbase-ix.html>
- SM: <https://web.stanford.edu/class/cs346/2015/redbase-sm.html>
- QL: <https://web.stanford.edu/class/cs346/2015/redbase-ql.html>
- EX: <https://web.stanford.edu/class/cs346/2015/redbase-ex.html>

Local mirrored pages:

- PF: `./redbase-pf.md`
- RM: `./redbase-rm.md`
- IX: `./redbase-ix.md`
- SM: `./redbase-sm.md`
- QL: `./redbase-ql.md`
- EX: `./redbase-ex.md`

This file is a condensed snapshot of the parts most relevant to implementation planning.

---

## PF — Paged File

### Purpose

PF is the bottom storage layer. It exposes page-oriented I/O over Unix files plus a shared buffer pool.

### Core model

- `PF_PAGE_SIZE = 4092` bytes usable page payload.
- `PF_BUFFER_SIZE = 40` pages in the buffer pool.
- Pages are fetched into memory and become **pinned**.
- Clients must explicitly unpin pages.
- Replacement policy: **LRU**.
- Deleted pages may be reallocated using **LIFO** reuse.

### Main abstractions

- `PF_Manager`: create/destroy/open/close paged files, allocate/dispose scratch pages.
- `PF_FileHandle`: iterate, fetch, allocate, dispose, dirty, unpin, force file pages.
- `PF_PageHandle`: access data pointer and page number for a pinned page.

### Important behavioral constraints

- Leaving pages pinned can exhaust the whole buffer.
- `CloseFile` should fail if pages of that file are still pinned.
- `ForcePages` flushes dirty pages without evicting them.
- Scratch pages live in the same buffer-pool budget.

### Rust design implications

- Model page pin/unpin with RAII or explicit guards.
- Track dirty state and pin count precisely.
- Make page-number allocation/reuse deterministic.
- Keep stats hooks close to actual read/write calls.

---

## RM — Record Management

### Purpose

RM stores unordered **fixed-length** records inside PF files.

### Suggested layout from the spec

- file header page,
- data pages with page-local metadata,
- permanent record identity via `(pageNum, slotNum)`.

### Required classes

- `RM_Manager`: create/destroy/open/close record files.
- `RM_FileHandle`: get/insert/delete/update records, force pages.
- `RM_FileScan`: scan all records or only those matching a condition.
- `RM_Record`: materialized copy of record data plus RID.
- `RID`: stable record identifier.

### Key design guidance from the spec

- Use a header page to store record size and free-space metadata.
- Avoid linear scans for insertion; maintain free-page information.
- Bitmaps are suggested for page slot occupancy.
- Files must grow arbitrarily; do not impose record-count caps.

### Scan semantics

`RM_FileScan::OpenScan` supports:

- `INT`, `FLOAT`, `STRING`,
- comparison operators `EQ/LT/GT/LE/GE/NE/NO_OP`,
- optional `value == NULL` meaning full scan.

### Rust design implications

- Fixed-length tuple layout should be byte-precise.
- RID should remain stable across unrelated inserts/deletes.
- Record materialization should copy bytes out of the buffer page.
- Free-space tracking should be first-class, not an afterthought.

---

## IX — Indexing

### Purpose

IX manages persistent single-attribute indexes using **B+ trees** stored in PF files.

### Required classes

- `IX_Manager`: create/destroy/open/close indexes.
- `IX_IndexHandle`: insert/delete entries, force pages.
- `IX_IndexScan`: condition-based scans over index entries.

### Logical entry model

Index entries represent `(attrValue, RID)`.

### Allowed simplifications

- lazy deletion is explicitly allowed,
- tombstones are allowed but lose some credit,
- RID overflow for one key may return an error unless bucket chaining is implemented.

### Important usage expectations

- deletions must not reappear in later scans,
- scans should support later `Delete`/`Update` operations from QL,
- recursive algorithms are strongly suggested.

### Rust design implications

- choose one explicit leaf format early,
- design scan state carefully so delete-during-scan can still work,
- keep key comparison semantics aligned with RM fixed-length string rules,
- separate tree structure metadata from payload encoding.

---

## SM — System Management

### Purpose

SM adds database-level behavior:

- create/destroy/open databases,
- create/drop tables,
- create/drop indexes,
- bulk load,
- help/print utilities,
- metadata catalogs.

### Command-line utilities expected by the spec

- `dbcreate DBname`
- `dbdestroy DBname`
- `redbase DBname`

### Catalogs

Two mandatory system catalogs:

- `relcat`
- `attrcat`

The spec strongly suggests keeping them open for the entire session.

### Metadata expectations

Typical catalog content includes:

- relation name,
- tuple length,
- attribute count,
- index count,
- attribute name,
- offset,
- type,
- length,
- index number.

### Other important constraints

- users may query catalogs,
- users must not drop or load catalogs,
- `Help` and `Print` must use the provided `Printer` abstraction,
- parser is provided and calls SM/QL methods.

### Rust design implications

- introduce a catalog API early; QL will depend on it heavily,
- define relation schema structs that can feed both storage and presentation,
- keep DDL/catalog mutations strongly validated.

---

## QL — Query Language

### Purpose

QL implements a restricted SQL-like language:

- `Select`
- `Insert`
- `Delete`
- `Update`

### Core requirement

The spec explicitly wants a **query tree / logical plan**, then a **physical plan**.

An iterator model is strongly suggested.

### Select semantics

- supports `Select attr-list From rel-list [Where ...]`
- also `Select *`
- conditions are conjunctions only (`And`)
- no duplicate elimination
- semantic validation is required for attribute disambiguation and type compatibility

### Performance expectation

QL should exploit indexes where straightforward:

- local selection `R.A = constant` via index scan,
- nested-loop join with indexed inner relation when applicable.

### Delete / Update expectations

- can use index-assisted access paths,
- must update/delete corresponding index entries,
- should build physical plans too,
- must print affected tuples using `Printer`.

### Query plan printing

The spec requires pretty-printable physical plans gated by `bQueryPlans`.

### Rust design implications

- separate logical expressions from executable operators,
- treat predicate binding/validation as its own phase,
- make table scans and index scans interchangeable operator nodes,
- preserve deterministic tuple formatting/output boundaries.

---

## EX — Personal Extension

### Purpose

A final extension roughly comparable in effort to one substantial project part.

### Proposal expectations

The proposal should specify:

1. functionality,
2. components modified,
3. overall design,
4. interface of major classes/methods,
5. informal method behavior,
6. demo plan.

### Example extension topics from the course page

- variable-length records,
- blobs/text objects,
- clustering,
- table partitioning,
- improved indexes,
- better joins,
- query optimization,
- more SQL,
- concurrency/recovery,
- compression,
- networking,
- GUI / visualization.

### Rust design implication

If the Rust codebase is intended to go beyond the assignment, it is worth preserving extension seams now: storage traits, operator abstractions, metadata versioning, and modular parser/planner hooks.

---

## Suggested implementation order for this repository

Based on the course material, the safest order is:

1. PF-compatible page/file/buffer abstractions
2. fixed-length RM with stable RID semantics
3. IX B+ tree with functional scans
4. SM catalogs and DDL/load path
5. QL planner + execution operators
6. optional extensions after the baseline is reliable

## Most important cross-cutting rules from the materials

- correctness first, but never ignore I/O behavior,
- keep interfaces stable while evolving internals,
- testing depth matters as much as feature completion,
- fixed-length strings are byte arrays, not conventional strings,
- later layers should depend on earlier layers, not bypass them.
