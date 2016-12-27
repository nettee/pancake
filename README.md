# Pancake

Pancake 是一个关系型数据库，它的实现受到了斯坦福 [CS346][cs346] 课程中 _RedBase_ 的启发，可以看作是 RedBase 的 Java 实现版。

Pancake is my personal Relational-DBMS project. It is inspired by _RedBase_ of Stanford [CS346][cs346] course and can be considered as a Java implementation of RedBase.

[cs346]: https://web.stanford.edu/class/cs346/2015/

Pancake 计划分为三个阶段进行实现，其中第一阶段和 RedBase 的功能相同。

Pancake is intended to envolve in the following three steps, first of which is the same as RedBase.

1. **单线程，无事务数据库 single-threaded DBMS without transaction** (working)
  + paged file (working)
  + record management (working)
  + index
  + system management
  + query language
2. 单线程，单事务数据库 single-threaded DBMS with single transaction
3. 多线程，多事务数据库 multi-threaded DBMS with multiple transactions

## 贡献 Contributing

接受各种形式的贡献，包括提交问题，修复代码，完善单元测试。期待您的 pull request。

Any types of contribution are welcome. Thanks.
