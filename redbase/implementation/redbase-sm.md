# The System Management Component RedBase Part 3: SM

Source: <https://web.stanford.edu/class/cs346/2015/redbase-sm.html>

Due Sunday, May 10, 11:59PM

- [Introduction](#intro)
- [Command Line Utilities](#utilities)
- [RedBase System Commands](#system)
- [**SM Interface**](#interface)
- [Return Codes and Error Handling](#error)
- [Metadata Management](#metadata)
- [Setup and Files](#setup)
- [The Parser](#parser)
- [The Printer Class](#printer)
- [Additional Commands](#additional)
- [Documentation, Testing, Submission, Etc.](#misc)

## Introduction

The *System Management* (*SM*) component provides:

1. Unix command-line utilities,
2. DDL commands,
3. system utilities,
4. metadata management.

The page assumes each RedBase relation is stored in its own RM file and each tuple is an RM record.

## Command-Line Utilities

The required utilities are:

- `dbcreate DBname`
- `dbdestroy DBname`
- `redbase DBname`

The database lives in a subdirectory named after `DBname`.

The page includes approximate code sketches for `dbcreate`, `dbdestroy`, and `redbase`, including initialization of `PF_Manager`, `RM_Manager`, `IX_Manager`, `SM_Manager`, and `QL_Manager`, followed by `RBparse(...)`.

## RedBase System Commands

The SM component covers:

- `create table ...`
- `drop table ...`
- `create index ...`
- `drop index ...`
- `load ...`
- `help;` / `help relName;`
- `print relName;`
- `set Param = "Value";`

The page gives syntax and expected behavior for each command.

## SM Interface

`SM_Manager` is the only class in the public SM interface.

```cpp
struct AttrInfo {
   char     *attrName;
   AttrType attrType;
   int      attrLength;
};

struct DataAttrInfo {
   char     relName[MAXNAME+1];
   char     attrName[MAXNAME+1];
   int      offset;
   AttrType attrType;
   int      attrLength;
   int      indexNo;
};

class SM_Manager {
  public:
       SM_Manager  (IX_Manager &ixm, RM_Manager &rmm);
       ~SM_Manager ();
    RC OpenDb      (const char *dbName);
    RC CloseDb     ();
    RC CreateTable (const char *relName, int attrCount, AttrInfo *attributes);
    RC DropTable   (const char *relName);
    RC CreateIndex (const char *relName, const char *attrName);
    RC DropIndex   (const char *relName, const char *attrName);
    RC Load        (const char *relName, const char *fileName);
    RC Help        ();
    RC Help        (const char *relName);
    RC Print       (const char *relName);
    RC Set         (const char *paramName, const char *value);
};
```

## Metadata Management

The basic project requires exactly two catalogs:

- `relcat`
- `attrcat`

These catalogs should be created at database creation time and normally kept open during the session. The page lists typical fields such as relation name, tuple length, attribute count, index count, attribute name, offset, type, length, and index number.

Users may query the catalogs, but they must not load into or drop them.

## Parser / Printer

The parser is provided. It calls SM and later QL methods.

The `Printer` class must be used for `Help` and `Print`, and later for QL output as well.

## Additional Commands

Administrative parser commands include:

- `reset buffer;`
- `print buffer;`
- `resize buffer i;`
- `print io;`
- `reset io;`

## Documentation / Testing / Submission

The component again requires comments, an `sm_DOC` writeup, and thorough testing. The page notes that interactive testing becomes much easier once the RedBase prompt is available.
