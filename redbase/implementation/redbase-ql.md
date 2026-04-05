# The Query Language Component RedBase Part 4: QL

Source: <https://web.stanford.edu/class/cs346/2015/redbase-ql.html>

Due Sunday, May 24, 11:59PM

- [Introduction](#intro)
- [The Language RQL](#rql)
- [**QL Interface**](#interface)
- [Printing Query Plans](#printing)
- [Miscellaneous](#misc)
- [Documentation, Testing, Submission, Etc.](#doc)

## Introduction

The *Query Language* (*QL*) component implements *RQL* (RedBase Query Language), a restricted SQL-like language supporting:

- `Select`
- `Insert`
- `Delete`
- `Update`

The page emphasizes that this is the most open-ended and design-heavy part of the core project, and that I/O-efficient execution strategy matters a lot.

## The Language RQL

### `Select`

Supported forms:

```sql
Select A1, A2, …, Am
From R1, R2, …, Rn
[Where ...];
```

and

```sql
Select *
From R1, R2, …, Rn
[Where ...];
```

The page describes semantic checks for relation existence, attribute disambiguation, and operand type compatibility.

### `Insert`

```sql
Insert Into relName Values (V1, V2, …, Vn);
```

### `Delete`

```sql
Delete From relName [Where ...];
```

### `Update`

```sql
Update relName Set attrName = AV [Where ...];
```

## QL Interface

The public interface is `QL_Manager` plus `QL_PrintError`.

The parser passes structured values using `RelAttr`, `Value`, and `Condition`.

```cpp
struct RelAttr {
  char *relName;
  char *attrName;
};

struct Value {
  AttrType type;
  void     *data;
};

struct Condition {
  RelAttr lhsAttr;
  CompOp  op;
  int     bRhsIsAttr;
  RelAttr rhsAttr;
  Value   rhsValue;
};

class QL_Manager {
 public:
      QL_Manager (SM_Manager &smm, IX_Manager &ixm, RM_Manager &rmm);
      ~QL_Manager ();
   RC Select (int nSelAttrs,
              const RelAttr selAttrs[],
              int nRelations,
              const char * const relations[],
              int nConditions,
              const Condition conditions[]);
   RC Insert (const char  *relName,
              int         nValues,
              const Value values[]);
   RC Delete (const char *relName,
              int        nConditions,
              const Condition conditions[]);
   RC Update (const char *relName,
              const RelAttr &updAttr,
              const int bIsValue,
              const RelAttr &rhsRelAttr,
              const Value &rhsValue,
              int nConditions,
              const Condition conditions[]);
};
```

## Planning and Execution Guidance

The page explicitly asks for:

- a query tree / logical plan,
- transformation into a physical plan,
- preferably an iterator execution model.

It also wants straightforward exploitation of indexes, especially for:

- local selections `R.A = constant`,
- nested-loop joins with indexed inner relations.

`Delete` and `Update` should also build physical plans and must update/delete index entries appropriately.

## Printing Query Plans

The component should pretty-print physical query plans when `bQueryPlans == 1`, toggled by:

- `queryplans on;`
- `queryplans off;`

## Miscellaneous / Submission

- Query and relation output must use the `Printer` class.
- System catalogs may be queried but not modified through RQL.
- The page stresses testing with large relations and complex queries.
