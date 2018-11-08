# Pancake

[![Open Source Love](https://badges.frapsoft.com/os/gpl/gpl.svg?v=102)](https://github.com/ellerbrock/open-source-badge/)
[![Build Status](https://travis-ci.org/nettee/pancake.svg?branch=master)](https://travis-ci.org/nettee/pancake)

## 概述

Pancake 是一个关系型数据库，它的实现受到了斯坦福 [CS346][cs346] 课程中 _RedBase_ 的启发，可以看作是 RedBase 的 Java 实现版。Pancake 是为了帮助理解数据库系统原理，因此实现较为简单，当前的目标是实现一个单线程、无事务数据库。与 RedBase 类似，Pancake 的实现分为五个部分：

[cs346]: https://web.stanford.edu/class/cs346/2015/

+ paged file (done)
+ record management (done)
+ index (working)
+ system management
+ query language

在基本功能实现后，Pancake 会逐步增加更高级的功能，包括：

+ 事务管理
+ 多线程并发
+ 事务可靠性

