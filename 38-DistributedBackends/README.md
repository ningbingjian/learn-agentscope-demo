# 第 38 课：DistributedBackends —— Redis / MySQL / OSS 与混合分布式存储

> 本课目标：把第 23、27 课中的 `DistributedStore` 从“会配置”推进到“知道每个后端到底负责什么、为什么要混合、什么时候应该选 Redis / MySQL / OSS”。

---

## 1. 为什么还需要这一课

第 23 课已经学过：

```text
Pod A ----\
           -> DistributedStore
Pod B ----/
```

第 27 课又把 Redis 放进 Kubernetes 多副本架构。

但这还没回答三个生产问题：

1. `DistributedStore` 里面到底有几种存储职责？
2. Redis、MySQL、OSS 的能力是否完全一样？
3. 为什么生产系统经常不是“全 Redis”或“全 MySQL”，而是混合？

本课专门解决这些问题。

---

## 2. DistributedStore 不是“一个 KV 接口”

2.0.1 的 `DistributedStore` 实际组合了：

```text
DistributedStore
│
├── AgentStateStore
│     Agent 对话/状态
│
├── BaseStore
│     RemoteFilesystem 工作区 KV
│
├── SandboxSnapshotSpec
│     Sandbox 工作区快照
│
├── SandboxExecutionGuard
│     多副本 Sandbox 分布式锁
│
├── MessageBus
│     消息/事件传输，可选
│
└── AsyncToolRegistry
      异步 Tool 状态，可选
```

其中前四个是本课重点。

---

## 3. 官方三大生产后端

### Redis

Artifact：

```text
agentscope-extensions-redis
```

能力：

```text
RedisAgentStateStore
RedisStore
RedisSnapshotSpec
RedisSandboxExecutionGuard
```

特点：低延迟、分布式锁能力强。

### MySQL / JDBC

Artifact：

```text
agentscope-extensions-mysql
```

能力：

```text
MysqlAgentStateStore
JdbcStore
JdbcSnapshotSpec
JdbcSandboxExecutionGuard
```

特点：适合已有数据库基础设施、需要 SQL 审计的系统。

### OSS

Artifact：

```text
agentscope-extensions-oss
```

能力：

```text
OssAgentStateStore
OssBaseStore
OssSnapshotSpec
```

**没有** `SandboxExecutionGuard`。

对象存储适合大文件，但不适合做分布式锁。

---

## 4. 能力矩阵

| 能力 | Redis | MySQL | OSS |
| --- | :---: | :---: | :---: |
| AgentStateStore | ✅ | ✅ | ✅ |
| BaseStore | ✅ | ✅ | ✅ |
| Sandbox Snapshot | ✅ | ✅ | ✅ |
| Sandbox Lock | ✅ | ✅ | ❌ |
| 低延迟小对象 | ⭐⭐⭐ | ⭐⭐ | ⭐ |
| 大二进制 | ⭐ | ⭐⭐ | ⭐⭐⭐ |
| SQL 审计 | ❌ | ✅ | ❌ |

启动本模块后查看：

```bash
./mvnw -pl 38-DistributedBackends spring-boot:run
curl http://localhost:18081/api/distributed/backends
```

---

## 5. 为什么本课默认不用真的启动 Redis/MySQL/OSS

如果自动化测试要求：

```text
Redis Server
MySQL Server
OSS AK/SK
```

那么一个基础课程模块会变得非常脆弱。

所以本课采取两层实验：

```text
第一层：H2 真跑 JDBC BaseStore/CAS
第二层：官方 Redis/MySQL/OSS extension 真正进入 classpath
```

这样可以验证真实 JDBC 语义，同时不依赖外部基础设施。

生产配置仍然严格按照官方后端实现。

---

## 6. 第一步：H2 MySQL compatibility mode

配置：

```java
DriverManagerDataSource dataSource = new DriverManagerDataSource();
dataSource.setDriverClassName("org.h2.Driver");
dataSource.setUrl(
    "jdbc:h2:mem:agentscope_distributed;MODE=MySQL;DB_CLOSE_DELAY=-1"
);
```

这里 H2 不是为了假装成生产 MySQL。

它只负责让：

```text
JdbcStore
```

可以在测试里真实执行 SQL。

---

## 7. 第二步：创建 JdbcStore

```java
BaseStore store = JdbcStore.builder(dataSource)
    .initializeSchema(true)
    .tableName("lesson_workspace_store")
    .build();
```

`BaseStore` 是 namespace + key 结构：

```text
namespace = [lesson, workspace]
key       = notes.md
value     = {content: hello}
```

写：

```java
store.put(
    List.of("lesson", "workspace"),
    "notes.md",
    Map.of("content", "hello")
);
```

读：

```java
StoreItem item = store.get(namespace, "notes.md");
```

`StoreItem` 除了 value，还有：

```text
version
```

---

## 8. version 为什么重要

多副本下最危险的问题之一是 lost update：

```text
Pod A 读 version=10
Pod B 读 version=10

Pod A 写 version=11
Pod B 再写自己的旧结果

A 的更新丢失
```

所以 `BaseStore` 提供：

```java
putIfVersion(namespace, key, value, expectedVersion)
```

只有版本匹配时才能写成功。

特殊值：

```text
expectedVersion = 0
```

表示：

> 只有 key 不存在时才创建。

---

## 9. 本课真实 CAS 实验

测试执行：

```java
store.putIfVersion(ns, key, Map.of("value", 1), 0);
```

得到第一版。

然后两个 writer 都拿旧 version：

```text
Writer A: expectedVersion = 1
Writer B: expectedVersion = 1
```

A 先更新成功：

```text
value = 2
version = 2
```

B 再提交：

```text
expectedVersion = 1
```

返回：

```text
false
```

这就是 server-side CAS，而不是 JVM 里的 `synchronized`。

---

## 10. 手动观察 CAS

先创建：

```bash
curl -X POST http://localhost:18081/api/distributed/jdbc/demo \
  -H 'Content-Type: application/json' \
  -d '{"content":"v1"}'
```

响应会包含：

```json
{
  "key": "demo",
  "value": {"content":"v1"},
  "version": 1
}
```

使用正确 version：

```bash
curl -X PUT http://localhost:18081/api/distributed/jdbc/demo/cas \
  -H 'Content-Type: application/json' \
  -d '{"expectedVersion":1,"value":{"content":"v2"}}'
```

再使用过期 version 1：

```bash
curl -X PUT http://localhost:18081/api/distributed/jdbc/demo/cas \
  -H 'Content-Type: application/json' \
  -d '{"expectedVersion":1,"value":{"content":"stale"}}'
```

第二次应该看到：

```text
updated = false
```

---

## 11. 第三步：理解一键 DistributedStore

Redis：

```java
DistributedStore redis =
    RedisDistributedStore.fromJedis(jedis);
```

MySQL：

```java
DistributedStore mysql =
    MysqlDistributedStore.create(dataSource);
```

OSS：

```java
DistributedStore oss =
    OssDistributedStore.create(
        ossClient,
        "bucket",
        "agentscope/"
    );
```

然后统一给 Harness：

```java
HarnessAgent.builder()
    .distributedStore(store)
    ...
```

业务 Agent 不需要知道后端类型。

---

## 12. 第四步：混合后端

真正重要的是：

```java
DistributedStore mixed = DistributedStore.builder()
    .agentStateStore(mysql.agentStateStore())
    .baseStore(mysql.baseStore())
    .sandboxSnapshotSpec(oss.sandboxSnapshotSpec())
    .sandboxExecutionGuard(redis.sandboxExecutionGuard())
    .build();
```

得到：

```text
Agent State      -> MySQL
Workspace KV     -> MySQL
Sandbox Snapshot -> OSS
Sandbox Lock     -> Redis
```

这是生产系统很自然的组合。

---

## 13. 本模块自己的 Mixed Store

为了不连接外部服务，本模块实际构造：

```text
AgentStateStore      -> InMemoryAgentStateStore
BaseStore            -> H2 JdbcStore
SandboxSnapshotSpec  -> H2 JdbcSnapshotSpec
ExecutionGuard       -> 默认 noop
```

查看：

```bash
curl http://localhost:18081/api/distributed/mixed
```

目的不是把这套配置用于生产，而是直接观察：

> `DistributedStore.builder()` 本质上是组件组合器。

---

## 14. Redis 什么时候最好

适合：

```text
高 QPS Session State
多 Pod
低延迟 Workspace KV
Sandbox 分布式互斥
小型短 TTL Snapshot
```

Redis lock 使用租约模型，因此要关注：

- lease TTL
- 长任务超时
- retry interval
- Redis 高可用

---

## 15. MySQL 什么时候最好

适合：

```text
公司已经有成熟 MySQL
状态需要 SQL 查询
希望方便审计
不想增加 Redis 依赖
```

`JdbcStore` 支持：

```text
MySQL / MariaDB
PostgreSQL
H2
SQLite
```

但是 `MysqlAgentStateStore` 和 `JdbcSandboxExecutionGuard` 中有 MySQL 专属能力，所以完整 `MysqlDistributedStore` 应使用真实 MySQL。

---

## 16. OSS 什么时候最好

最大的优势：

```text
大容量二进制对象
```

例如 Sandbox 的整个工作区快照：

```text
20 MB
100 MB
500 MB
```

长期塞 Redis 的成本和内存压力很大。

OSS 更自然。

但是：

```text
OSS != distributed lock
```

所以 OSS 常和 Redis 组合。

---

## 17. 一个常见生产组合

```text
                 HarnessAgent
                      |
              DistributedStore
                      |
       +--------------+--------------+
       |              |              |
       v              v              v
     Redis          MySQL           OSS
       |              |              |
    locks          audit/state     snapshots
    hot KV         structured KV   large blobs
```

不一定所有公司都这样选，但应该按数据特性而不是“统一技术栈看起来整齐”来选。

---

## 18. 显式配置优先级

AgentScope 2.0.1 的原则：

```text
显式 builder 配置
      >
distributedStore 自动注入
      >
本地默认实现
```

所以你可以：

```java
.distributedStore(redis)
.stateStore(customStateStore)
```

只覆盖某一个组件。

---

## 19. 自动化测试

```bash
./mvnw -pl 38-DistributedBackends test
```

测试验证：

1. `JdbcStore` 在 H2 上真实建表、写入和读取。
2. `putIfVersion` 真正阻止 stale writer。
3. `DistributedStore.builder()` 可以组合 State / BaseStore / Snapshot。
4. Redis、MySQL、OSS 三个官方 extension 都实际存在于 classpath。

没有 Redis mock、MySQL mock 或 OSS mock 来冒充生产后端。

---

## 20. 本课最终心智模型

```text
DistributedStore
      |
      +-- 状态是小对象、高频？ ------> Redis / MySQL
      |
      +-- Workspace 要 SQL 审计？ ---> MySQL
      |
      +-- Snapshot 很大？ ----------> OSS
      |
      `-- 需要跨 Pod 锁？ ----------> Redis / MySQL
```

关键结论：

> `DistributedStore` 的价值不是强迫所有数据放在一个数据库，而是给 Agent 一个统一入口，同时允许底层按数据特性组合最合适的后端。
