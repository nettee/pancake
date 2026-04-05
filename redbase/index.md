# CS346 Reference Context

Source: <https://web.stanford.edu/class/cs346/2015/>

This directory stores a curated local snapshot of the Stanford CS346 / RedBase materials that are most useful as implementation context for a Rust reimplementation.

## Directory layout

- `overview/`
- `course-overview.md`: course scope, schedule, handouts, and project roadmap.
  - `redbase-project-reference.md`: RedBase overview, logistics, grading/testing constraints, and FAQ notes.
- `implementation/`
  - `component-implementation-notes.md`: condensed implementation-oriented notes for PF / RM / IX / SM / QL / EX.
- `redbase-*.md`: local mirrors of the main PF / RM / IX / SM / QL / EX spec pages.

## Selection rationale

- Kept: project overview, part-by-part specifications, logistics, testing/grading expectations, and FAQ constraints.
- Kept: lecture/handout map so later implementation work can trace back to relevant topics.
- Not mirrored verbatim: slide PDFs / PPTs and old lecture-note HTML pages, because they are supplementary and much larger; the overview file records where they live.

## Recommended reading order

1. `overview/redbase-project-reference.md`
2. `implementation/component-implementation-notes.md`
3. the corresponding `implementation/redbase-*.md` page for the component currently being implemented
