---
id: 20260405-pf-rust-design
name: Pf Rust Design
status: implemented
created: '2026-04-05'
---

## Overview

### Problem Statement
- 需要根据 rednote PF 讲义，为 `src/pf.rs` 制定一份可落地的实现方案，作为后续 PF/RM/IX 分层实现的底座。
- 当前 `src/pf.rs` 只有最小占位实现，若过早沿用 C++ handle/pointer 风格，后续会在 Rust 的所有权、生命周期和页固定语义上产生系统性摩擦。

### Goals
- 保留 RedBase PF 的核心语义：分页文件、页分配/释放、页扫描、dirty/flush、buffer pin 语义、已释放页的 LIFO 复用。
- 对外 API 使用 Rust 风格，优先对所有权和生命周期友好，不机械照搬 `PF_Manager/PF_FileHandle/PF_PageHandle` 的 C++ 形状。
- 从一开始采用 TDD，所有非平凡实现必须与测试同时编写，并将这一原则补充进 `AGENTS.md`。

### Scope
- 本次讨论范围聚焦 `src/pf.rs` 的 API 设计、模块分层、实现顺序、测试顺序，以及与现有 `common`/整体 RedBase 架构的衔接。
- 不包含 RM/IX/SM/QL 的具体实现。

### Constraints
- 设计要延续 RedBase 的语义和命名风格，但 Rust-facing API 必须更自然。
- 初始实现保持单线程，不引入并发控制。
- 页格式应按字节精确定义，不依赖将 Rust struct 直接映射到磁盘布局。

### Ideas & Approaches
- 用 `PfManager + PfFile + ReadPageGuard/WritePageGuard` 替代公开的 C++ 风格 file/page handle 组合。
- 将 pin/unpin 变为 RAII guard 生命周期，而将 page id、allocate/dispose、flush 等存储语义保持为显式 API。
- 第一阶段先做正确的 paged-file 边界和测试，再逐步补全 buffer pool、LRU、dirty eviction 等更完整的 PF 语义。

### Open Questions
- 第一版是否立即实现完整 buffer pool/LRU，还是先落地 direct I/O + 最小缓存切片再演进。
- `allocate_page` 第一版返回 `PageId` 还是直接返回 `WritePageGuard`。
- scratch pages 是否推迟到更后面的阶段。

### Success Criteria
- 可以据此开始 `src/pf.rs` 的 TDD 实现，并且设计不会在后续 RM/IX 层引入明显的所有权/生命周期障碍。
- 测试顺序和 API 轮廓清晰，能够支持后续按小步红绿重构推进。

## Research

### Existing System
- `src/pf.rs` 当前仅有 `PfManager` 空壳；`src/buffer.rs`、`src/rm.rs`、`src/ix.rs` 等也仍是占位。
- `src/common.rs` 已提供 `PageId`、`Rid`、基础错误类型和小端字节读写 helper，可作为 PF 的共享基础设施。
- `redbase/implementation/redbase-pf.md` 明确了 PF 语义：page-oriented I/O、buffer pool、pin/unpin、dirty flush、scan 顺序、页释放后的 LIFO 复用、scratch pages、PF 层错误语义。
- `redbase/implementation/component-implementation-notes.md` 将 PF 总结为底层存储边界，并明确指出 Rust 设计应使用 RAII/guard 表达 pin/unpin。
- `specs/design/rust.md` 已确定项目方向：保持 RedBase 语义，但 Rust API 不必复制 C++；优先使用 RAII page guards；避免自引用设计；保持单线程；按底向上实现。
- 当前仓库中尚未形成现成的 PF 测试模式，本次 PF 设计需要同时建立测试优先的实现路径。

### Options Evaluated
1. **延续 RedBase 语义，但公开 Rust 风格 API（推荐）**
   - 保留页号、分配/释放、flush、扫描等显式语义。
   - 用 guard 取代显式 `UnpinPage`/`MarkDirty` 的主要使用路径。
2. **直接照搬 C++ `PF_FileHandle/PF_PageHandle` 公开接口**
   - 语义贴近原始讲义，但会暴露更强的手动资源管理痕迹，不利于 Rust 的借用边界设计。
3. **完全隐藏 PF 语义，只暴露高层页对象接口**
   - 更“Rust 风格”，但会模糊 RedBase PF 的教学语义，也不利于 RM/IX 清晰依赖 PF 能力。

### Recommendation
- 采用“RedBase 语义 + Rust API 形状”的折中方案：公开 `PfManager`、`PfFile` 和短生命周期 page guards；显式保留 page allocation/disposal/flush/scan；将 pin/unpin 降为 guard 的生命周期行为。
- 实现路径应从 TDD 出发，先完成文件生命周期、单页分配/读写/持久化，再逐步扩展到扫描、页复用、dirty/eviction 和 buffer pool。

## Design

### Architecture
`PfManager` → 打开/创建/销毁分页文件  
`PfFile` → 表示一个打开的分页文件实例，负责页分配、读取、写入、释放、扫描、flush  
`ReadPageGuard` / `WritePageGuard` → 表示已 pin 的页视图，drop 时自动 unpin

### Public API Direction
- `PfManager`
  - `create_file(path)`
  - `destroy_file(path)`
  - `open_file(path) -> PfFile`
- `PfFile`
  - `allocate_page() -> Result<PageId, PfError>`
  - `get_page(page_id) -> Result<ReadPageGuard<'_>, PfError>`
  - `get_page_mut(page_id) -> Result<WritePageGuard<'_>, PfError>`
  - `dispose_page(page_id)`
  - `first_page_id()` / `next_page_id(current)`
  - `flush_page(page_id)` / `flush_all()`
- `ReadPageGuard`
  - `page_id()`
  - `data() -> &[u8]`
- `WritePageGuard`
  - `page_id()`
  - `data() -> &[u8]`
  - `data_mut() -> &mut [u8]`

### Key Design Decisions
- 不公开 `unpin_page`：guard drop 自动 unpin。
- 不鼓励上层长期持有页内借用：上层应传递 `PageId` 或复制出的数据，而不是跨步骤保存页内引用。
- 第一版 `allocate_page` 优先返回 `PageId`，避免在页分配与文件元数据更新之间形成复杂可变借用冲突。
- PF 常量建议显式采用 RedBase 语义：`PF_PAGE_SIZE = 4092`、`PF_BUFFER_SIZE = 40`，而不是直接复用当前 `common::PAGE_SIZE = 4096`。
- scratch pages、反向扫描、完整 LRU 统计可后置，不阻塞第一阶段落地。

### Internal Layering
1. **on-disk file format**
   - file header
   - free-page stack / disposed-page metadata
   - page validity metadata
2. **page I/O boundary**
   - 读页 / 写页
   - 分配页 / 释放页
   - 前向扫描
3. **buffer management**
   - frame table
   - pin count
   - dirty bit
   - LRU replacement
4. **page guards**
   - 对外暴露短生命周期只读/可写页访问

### Ownership & Lifetime Rules
- 避免自引用结构，不在持有 buffer/file 状态的对象里长期保存指向页内字节的引用。
- page guard 只暴露短生命周期借用，不允许将 `&[u8]` / `&mut [u8]` 脱离 guard 存活。
- 借用作用域保持短小，避免同时持有多个不必要的可变 guard。

### TDD Implementation Sequence
1. 文件生命周期
   - create file
   - open existing file
   - create existing file fails
   - open missing file fails
2. 单页分配
   - allocate first page
   - page id valid
   - new page bytes deterministic
3. 读写持久化
   - write bytes
   - flush/close
   - reopen and read equal bytes
4. 多页与扫描
   - allocate multiple pages
   - verify numeric order
   - forward scan correct
5. 释放与复用
   - dispose page
   - next allocation reuses latest disposed page (LIFO)
   - scan skips disposed pages
6. guard 语义
   - drop guard releases pin
   - pinned page blocks disallowed close/eviction path as designed
7. dirty/eviction
   - read guard does not dirty
   - write path persists after flush
   - dirty page eviction writes back

### Files to Modify
- `src/pf.rs` — PF public API、file format、buffer/page guard 实现与测试入口。
- `src/common.rs` — 仅在 PF 错误类型、常量或共享 helper 需要抽取时做最小补充。
- `AGENTS.md` — 已补充 TDD-first 的工程规则。

## Plan

- [x] Phase 1: 建立 PF 最小可测切片
  - [x] 为 file lifecycle 编写失败/成功测试
  - [x] 实现 `PfManager::{create_file, destroy_file, open_file}`
  - [x] 为单页分配与基本读写补测试并实现
- [x] Phase 2: 扩展页语义
  - [x] 为 forward scan 编写测试并实现 `first_page_id` / `next_page_id`
  - [x] 为 `dispose_page` + LIFO 复用编写测试并实现
  - [x] 引入 page guards 并用测试固定 pin/unpin 语义
- [x] Phase 3: 完成 buffer/flush 行为
  - [x] 为 dirty flush 编写测试并实现
  - [x] 在小 buffer 场景下为 eviction/LRU 编写测试并实现
  - [x] 回归验证持久化与扫描语义

## Notes

- 当前讨论已经完成代码库调研，并形成了明确的 API/分层/测试顺序建议，因此 spec 适合直接进入 `designed`。
- 第一实现切片优先考虑正确性和 API 边界，不要求一次性覆盖 PF 全部高级能力。

### Implementation

- `src/pf/mod.rs` — defines the Phase 3 default PF buffer capacity constant.
- `src/pf/manager.rs` — initializes opened PF files with the in-memory buffer pool state.
- `src/pf/file.rs` — adds minimal buffered page caching, dirty-frame tracking, LRU eviction, explicit flush-all write-back, and fail-loud drop-time close flushing.
- `src/pf/page.rs` — changes write guards to hand dirty bytes back to the buffer pool on drop instead of writing through immediately.
- `src/pf/tests.rs` — adds Phase 3 coverage for dirty flush behavior, dirty-page persistence on file drop, and small-buffer LRU eviction with scan regression.
- Coding-time decision: Phase 3 keeps the existing owned-buffer page guards and updates the shared buffer pool only when a write guard drops, which avoids self-referential borrowing while still enabling dirty caching.
- Deviation from the longer-term design: the buffer pool remains a minimal PF-internal implementation rather than a separate reusable `buffer` module, and still keeps `PF_PAGE_SIZE` aligned with `common::PAGE_SIZE = 4096`.

### Verification

- 新增 3 个 Phase 3 PF 单元测试；当前 PF 单元测试共 15 个，`cargo test` 结果为 15 passed, 0 failed。
- 验证 dirty page 在 `flush_all` 前仅停留在缓冲区，执行 `flush_all` 或关闭文件后会持久化到磁盘。
- 在小 buffer 场景下验证了 LRU eviction 会写回脏页，且前向扫描仍会跳过 disposed pages。
- 当前已知限制：尚未拆分为独立 `buffer` 模块，也未实现更完整的 replacement metrics / scratch pages 语义。
