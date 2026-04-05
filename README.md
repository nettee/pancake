# Pancake

`pancake` is a deliberately minimal Rust scaffold inspired by the RedBase project structure.

The codebase is kept intentionally sparse so it can compile cleanly without committing to premature database APIs or ownership models.

## Current scaffold

- Rust 2024 edition
- Stable toolchain friendly
- Small set of low-level shared primitives in `common`
- Sparse placeholder modules: `pf`, `buffer`, `rm`, `ix`, `sm`, `ql`

## Guiding principle

- Prefer neutral placeholders over speculative interfaces
- Keep modules present, but avoid shaping future architecture too early
- Add only primitives that are clearly justified by the current scaffold
