# 模块设计

由于本项目是受到斯坦福 [CS346][cs346] 课程中 RedBase 的启发，因此模块设计和 RedBase 一致，包括五层结构：

+ **Paged File**: 最底层结构，将文件分为 **页** ，向上层模块提供文件I/O操作接口
+ **Record Management**: 在文件中存储 **记录** ，提供存取记录的功能
+ **Indexing**: 在无序的数据记录之上创建 **索引** ，用来加速记录的存取
+ **System Management**: 提供命令行工具，处理DDL命令
+ **Query Language**: 处理SQL查询语言

RedBase 项目是使用 C++ 实现的，而本项目使用 Java 实现，因此会有一些更 **面向对象** 的设计。但总体结构与 RedBase 相似。

## 参考文档

+ [Stanford CS346 - Database System Implementation - Spring 2015][cs346]

[cs346]: (https://web.stanford.edu/class/cs346/2015/)
