# Rust Design Notes

This document summarizes the current Rust-specific implementation direction for `pancake`, a Rust rewrite of the Stanford RedBase project.

The goal is to preserve the core RedBase semantics and layer boundaries while adapting APIs, ownership, and lifetimes to Rust.

## Rust Version

### Edition

- Use `edition = "2024"`.

### Toolchain policy

- Develop on the current stable toolchain.
- At the time of writing, the recommended development toolchain is Rust `1.94.x` stable.
- Set `rust-version = "1.85"` in `Cargo.toml`.

### Rationale

- Rust 2024 is the default edition for new projects and is stable.
- Developing on stable keeps the project aligned with mainstream tooling, dependencies, and documentation.
- Declaring `rust-version = "1.85"` keeps the minimum supported Rust version reasonable while still allowing Rust 2024.
- Do not depend on nightly-only features unless a future design decision explicitly justifies them.

## Implementation Order

The implementation should remain **bottom-up**, following the original RedBase dependency structure, but with **thin vertical slices** to validate APIs early.

The intended order is:

1. **Core primitives**
   - common types (`PageId`, `Rid`, `SlotId`, attribute types, comparison operators)
   - error model and shared constants
   - explicit byte encoding helpers
2. **PF foundations**
   - paged file format
   - page allocation, deallocation, read, write, file open/close
3. **Buffer pool and page guards**
   - frame table, pin counts, dirty tracking, replacement policy
   - RAII-style page guards for safe pin/unpin behavior
4. **Record page layout**
   - slotted-page or equivalent fixed-record page layout
   - single-page insert/delete/update/get
5. **RM layer**
   - record files, file headers, free-space tracking
   - record scans and stable `RID` semantics
6. **Index page layout**
   - B+ tree internal and leaf page formats
   - split/search logic and key encoding
7. **IX layer**
   - index create/open/close
   - insert/search/scan
   - simplified delete strategy first
8. **Catalog and system metadata**
   - relation schemas, attribute metadata, index metadata
   - minimum table/index management operations
9. **Minimal vertical slice**
   - create table
   - insert tuple
   - full scan
   - create index
   - point lookup through an index
10. **SM and QL expansion**
    - DDL, load/help/print
    - query execution for insert/select/delete/update

### Bottom-up, but not layer-locked

The project should not wait for each layer to feel “complete” before touching the next one. The intended workflow is:

- finish the minimum viable storage boundary,
- validate it with a higher-level use case,
- then continue deepening the lower layers.

This is especially important in Rust, where an awkward storage API can create widespread ownership and lifetime friction later.

## Key Rust Design Principles

### Preserve RedBase semantics, redesign Rust-facing APIs

RedBase semantics should remain recognizable:

- paged files
- pinned pages
- explicit dirty tracking
- record files with stable record identifiers
- B+ tree indexes over `(key, RID)`
- SM/QL layered over PF/RM/IX

However, the Rust API does **not** need to mimic the original C++ object model. In particular:

- avoid copying C++ handle patterns mechanically,
- avoid raw-pointer-style page access,
- prefer Rust-native lifetime boundaries and ownership rules.

### Use RAII page guards instead of manual pin/unpin APIs

The original RedBase PF interface exposes explicit pin/unpin behavior. Rust should preserve that behavior, but express it through scoped guards.

Preferred direction:

- `fetch_page_read(page_id) -> ReadPageGuard`
- `fetch_page_write(page_id) -> WritePageGuard`
- dropping a guard automatically unpins the page

This keeps the underlying semantics while making correct usage easier and leakage harder.

### Keep logical identifiers separate from in-memory references

Higher layers should pass around logical identifiers such as:

- `PageId`
- `Rid`
- `SlotId`

They should generally not retain long-lived references into page memory.

This keeps APIs simpler and avoids lifetime entanglement across RM, IX, and QL.

### Avoid self-referential designs

Do not design structs that store references into memory they indirectly own through the buffer pool.

Examples to avoid:

- records that permanently borrow page bytes,
- index scan state that keeps nested references into mutable tree state,
- page-backed objects whose internals depend on self-referential borrowing.

Prefer:

- owned metadata,
- short-lived page views,
- copied record materialization where appropriate.

### Treat on-page layout as bytes, not as normal Rust structs

Page contents should be managed through explicit byte-level encoders/decoders and offset helpers.

The project should avoid assuming that plain Rust structs can be safely mapped onto persistent page formats.

That means:

- define exact offsets and sizes,
- keep page header access explicit,
- make serialization and deserialization deterministic.

### Prefer simple enums over deep trait hierarchies unless extensibility clearly pays off

For this project, many concepts are better represented by enums and compact data structures than by C++-style polymorphic class trees.

Examples include:

- attribute types,
- comparison operators,
- page kinds,
- simple plan/operator categories.

Traits should be introduced only where they clearly improve testability or reduce duplication.

### Keep borrowing scopes short

Long-lived borrows over the buffer pool or tree state will create avoidable friction.

Preferred style:

- fetch a page,
- extract or mutate what is needed,
- drop the guard,
- continue with the next step.

This should be reflected in internal APIs, recursive tree logic, and scan implementations.

## Explicit Project Decisions

### No concurrency in the initial implementation

The first implementation should be **single-threaded**.

This is an intentional design decision, not a missing feature.

Reasons:

- the original RedBase project does not require concurrent execution,
- Rust ownership and page-lifetime issues are already substantial in a single-threaded design,
- adding locks, concurrent buffer management, or transactional concurrency too early would multiply complexity without helping the core educational goals.

If concurrency is explored later, it should be treated as an explicit extension after the single-threaded storage and query path is stable.

### No nightly-only dependency in the base design

The baseline implementation should remain on stable Rust.

Nightly is out of scope unless a future extension requires it and the benefit is clearly documented.

### Simplify where the original spec already permits simplification

The Rust implementation should prioritize correctness and clarity over maximal completeness.

Examples:

- start with fixed-length RM records,
- prefer a simplified but correct B+ tree delete strategy first,
- delay advanced optimization work until correctness and API quality are stable.

### API ergonomics are part of correctness

In this project, API design is not separate from correctness.

If page access, record access, or scan APIs encourage misuse, ownership problems and resource leaks will appear in higher layers. Therefore:

- safe defaults matter,
- guard-based resource management matters,
- explicit ownership boundaries matter.

## Testing Implications

The implementation plan should emphasize tests at storage boundaries first.

Priority areas:

- page layout encoding/decoding,
- record page invariants,
- pin/unpin correctness,
- dirty-page flushing behavior,
- free-space tracking,
- B+ tree split/search behavior,
- delete visibility guarantees.

Higher-level SM and QL work should be added only after these lower-level invariants are reliable.

## Summary

The Rust rewrite should keep the RedBase architecture **bottom-up**:

- PF first,
- RM and IX on top of PF,
- SM on top of RM/IX,
- QL last.

But the implementation should be shaped by Rust:

- use stable Rust 2024,
- design storage APIs around ownership and scoped guards,
- avoid self-referential and pointer-heavy designs,
- keep page formats explicit and byte-oriented,
- defer concurrency,
- prefer a correct and clear single-threaded foundation before advanced extensions.
