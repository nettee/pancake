# CS346 Spring 2015 Course Overview

Source: <https://web.stanford.edu/class/cs346/2015/>

## Course focus

CS346 is a database system implementation course centered on building a complete single-user DBMS named **RedBase**. The project progresses from file/page management through records, indexes, catalogs, and query execution, then finishes with a personal extension.

The site makes clear that the project is the center of the course, while lectures provide implementation guidance and advanced database-system techniques.

## Project roadmap

The RedBase project is divided into six stages:

1. **PF** — Paged File (provided baseline component)
2. **RM** — Record Management
3. **IX** — Indexing (B+ tree based)
4. **SM** — System Management
5. **QL** — Query Language
6. **EX** — Personal Extension

## Key schedule / handouts map

### Week 1

- Intro to course, DBMS review, RedBase overview
- Handouts:
  - RedBase Part 0: PF
  - RedBase Part 1: RM
  - Old Lecture Notes: Overview
- Buffer/file review lecture
  - Slides: Buffer Management
  - Old Lecture Notes: Buffer

### Week 2

- Buffer Management
  - Slides: Buffer Manager Extra
- Page Layout and File of Records
- **RM due: Apr 12**

### Week 3

- RedBase IX component, indexing and B+ tree review
  - Slides: B+/B-Link Trees
  - RedBase Part 2: IX
  - Old Lecture Notes: Indexing
- Concurrency in Indexing, B-Link tree

### Week 4

- RedBase SM and QL components, metadata and query processing review
  - RedBase Part 3: SM
  - RedBase Part 4: QL
  - Old Lecture Notes: Metadata
  - Old Lecture Notes: QL
- Query processing lecture
  - Slides: Cost Models
  - Old QP Page
- **IX due: Apr 26**

### Week 5+

- Recovery (ARIES)
- Guest lectures from Oracle / Pivotal / Databricks / Hadoop / SAP / Twitter / LogicBlox
- Database Analytics / RedBase EX component
- **SM due: May 10**
- **EX proposal due: May 17**
- **QL due: May 24**
- **EX final demo: Jun 5**

## Readings and supporting material worth revisiting later

- Buffer-management slides and notes
- B+ / B-Link tree slides and indexing notes
- Metadata and query-processing notes
- ARIES slides and paper
- Cost-model references

Useful linked references from the site:

- ARIES paper
- B-Link tree paper
- Old lecture notes under `notes/old/`

## Course expectations that matter for a reimplementation

- Students are expected to understand databases already and focus on implementation details.
- Efficiency is emphasized throughout, especially I/O behavior.
- Testing is treated as crucial, not optional.
- Later components assume solid abstractions from earlier ones.

## Why this matters for the Rust port

The course ordering strongly suggests a clean Rust architecture:

1. page/file layer first,
2. fixed-length record manager on top,
3. B+ tree index as a sibling over PF,
4. catalog + DDL layer over RM/IX,
5. query layer over SM/RM/IX,
6. optional extension only after baseline completeness.
