# Pancake

## Language Rules

- Write all README files in English.
- Write all code comments in English.
- Use English consistently for documentation, inline comments, and code-facing text across the project.

## Repository Rules

- Use `main` as the default base branch.
- Submit all pull requests against `main` unless explicitly instructed otherwise.

## Engineering Rules

- Use TDD from the start for all non-trivial implementation work.
- Every implementation change must be written together with tests; do not land logic without corresponding tests.
- Prefer small red-green-refactor steps, especially for storage and API boundary code.
- Add concise English comments around non-obvious or important code paths (for example RAII cleanup, guard semantics, ownership boundaries, and persistence-critical logic).
