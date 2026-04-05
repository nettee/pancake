# RedBase Project Reference

Sources:

- <https://web.stanford.edu/class/cs346/2015/redbase.html>
- <https://web.stanford.edu/class/cs346/2015/redbase-logistics.html>
- <https://web.stanford.edu/class/cs346/2015/redbase-faq.html>

## Project overview

RedBase is a complete single-user relational DBMS built incrementally. The project structure is:

- **PF**: provided paged-file layer
- **RM**: unordered fixed-length record files
- **IX**: B+ tree indexing
- **SM**: database utilities, catalogs, DDL
- **QL**: query/update language
- **EX**: personal extension

The official project page stresses three themes:

1. build the system component-by-component,
2. preserve clear interfaces between components,
3. care about **I/O efficiency** from the start.

## Logistics and constraints

### Suggested layout

The course strongly recommends keeping all RedBase source code in a **single directory** for building and grading.

### Supplied baseline

- PF component is provided.
- Later setup scripts provide headers, test shells, parser, and stubs.

### Testing expectations

- Provided tests are only starter tests.
- TA tests are much more comprehensive.
- Students are expected to describe their testing process in each component doc.
- Sharing tests is allowed only if shared publicly to the whole class.

### Submission expectations

Each component requires:

- working code,
- a 1–2 page plain-text design/test document (`rm_DOC`, `ix_DOC`, `sm_DOC`, `ql_DOC`),
- sufficient code comments,
- compatibility with the provided interfaces.

### Important grading dimensions

Each part is graded on:

- functionality,
- documentation,
- design choices,
- modularity/correctness.

The logistics page explicitly says a system can pass tests but still lose points for incorrect implementation, such as large memory leaks.

### Valgrind requirement

The FAQ/logistics pages say Valgrind is expected and submitted programs must run with:

`ERROR SUMMARY: 0 errors from 0 contexts`

### Efficiency emphasis

There is an **I/O Efficiency Contest**. The specs repeatedly encourage minimizing page reads/writes and using PF statistics.

## FAQ constraints worth carrying into a Rust implementation

### Earlier components can evolve

The FAQ explicitly allows modifying previous components later, as long as the specified public interfaces are preserved.

### String semantics

Fixed-length string fields are raw byte strings of length `n`, not C strings.

Implications:

- embedded `\0` is legal,
- comparisons are bytewise over the full fixed width,
- when comparing strings of different lengths, pad the shorter one with `\0`.

This is important for Rust too: do not model these fields as ordinary UTF-8 `String` semantics only.

### Memory-use rule

Large custom caches outside the PF buffer are considered invalid for the course efficiency model.

The sanctioned escape hatch is PF **scratch pages**.

### PF stats must remain trustworthy

If PF is modified, the I/O statistics behavior must remain intact.

## Recommended local design takeaways

For a Rust version, the most reusable project constraints are:

- preserve layer boundaries,
- keep file/page semantics explicit,
- treat record strings as fixed byte arrays,
- make buffer/I/O accounting observable,
- design for testability and deterministic scans,
- avoid hidden in-memory caches that would undermine the intended execution model.
