# 52-StateConcurrencyAndConsistency

## 1. 这一课解决什么问题

前面已经分别学过多用户并发、AgentState、DistributedStore、JDBC CAS、Async Tool 和 Wakeup，但生产系统最危险的问题往往发生在它们组合之后：**两个请求、两个线程、甚至两个 Pod 同时碰同一个 session 时，谁拥有最终状态？**

本课把问题分成两层：

```text
单 JVM / 单 Agent 实例
        ↓
AgentScope same-session serialization
        ↓
避免同一会话在一个实例内部并发修改

多 JVM / 多 Pod
        ↓
本地 serialization 已经失效
        ↓
需要共享 CAS / distributed lock / idempotency / transaction
```

## 2. 单实例：same-session serialization

AgentScope Java 2.0.1 的 `AgentBase` 为具体 Agent 提供 per-key call gate。`ReActAgent` 使用 `(userId, sessionId)` 作为串行化槽位，因此同一 session 的两个 call 会 FIFO 执行，而不同 session 可以并行。

本课用 `ConcurrentProbeModel` 让一次模型调用停留约 120ms，并记录同时进入 Model 的最大调用数：

```text
same session
call A ────────────────┐
                       ├─ max active = 1
call B waits ──────────┘

different sessions
call A ────────────────
call B ────────────────
       max active >= 2
```

注意：这只是**同一个 JVM 内、同一个 Agent 实例上的协调**，它不是分布式锁。

## 3. 多 Pod：为什么还需要 CAS

假设两个 Pod 同时读到：

```text
session version = 10
```

然后各自完成了一次 Agent 推理：

```text
Pod A wants version 11
Pod B wants version 11
```

如果直接 `put()`：

```text
A 写入
B 覆盖 A
=> lost update
```

本课使用 2.0.1 官方 `JdbcStore.putIfVersion()`：

```text
UPDATE ...
WHERE key = ?
  AND version = 10
```

两个 writer 使用相同 `expectedVersion=10` 时只有一个能成功，另一个得到 `false`，必须重新 load 最新状态后决定 retry / merge / abort。

`expectedVersion=0` 代表 create-if-absent，底层依靠主键唯一约束实现原子创建。

## 4. CAS 不等于分布式事务

这是本课必须记住的边界：

```text
CAS
= 单个 versioned key 的原子比较并写入

Transaction
= 多条记录要么全部成功，要么全部失败
```

如果一次业务需要同时写：

```text
AgentState
Business Order
Outbox Event
Audit Log
```

不能因为有 `putIfVersion()` 就宣称它们已经具备事务一致性。生产中仍要考虑数据库事务、Outbox、Saga 或补偿。

## 5. Idempotency：重复事件不是异常情况

以下都可能重复：

- HTTP 客户端 retry
- Channel webhook 重放
- MQ redelivery
- Async Tool completion 回调
- Wakeup 重复入队
- Pod crash 后重新消费

因此消费者要把：

```text
eventId / requestId
```

当成业务幂等键。本课再次使用 `putIfVersion(..., expectedVersion=0)` 做 claim：

```text
pod-a claim event-123 -> true
pod-b claim event-123 -> false
```

只有第一个消费者继续做副作用。

## 6. 推荐生产执行链

```text
Request
  ↓
Idempotency check
  ↓
Resolve (userId, sessionId)
  ↓
Single-node/session serialization
  ↓
Load shared state + version
  ↓
Agent execution
  ↓
CAS save(expectedVersion)
  ↓
CAS conflict?
  ├─ no  -> publish/outbox
  └─ yes -> reload + retry/merge/abort
```

如果必须确保同一 session 永远只在一个 Pod 执行，也可以在更外层增加 Redis/MySQL distributed lock，但锁仍需要 lease、fencing token 和 crash recovery 设计。

## 7. 本课代码

- `ConcurrentProbeModel`：观察 Model 是否真正重叠执行
- `ConsistencyService.serializationDemo()`：验证 same-session 与 different-session
- `JdbcStore` + H2：本地模拟共享数据库
- `casRaceDemo()`：两个 Pod 使用同一 stale version 竞争
- `idempotencyDemo()`：重复事件只有一次 claim 成功

默认不需要 Redis/MySQL 外部服务，也不需要模型 API Key。

## 8. 启动

```bash
./mvnw -pl 52-StateConcurrencyAndConsistency spring-boot:run
```

```bash
curl http://localhost:18081/api/consistency/serialization
curl http://localhost:18081/api/consistency/cas-race
curl http://localhost:18081/api/consistency/idempotency
curl http://localhost:18081/api/consistency/architecture
```

## 9. 测试

```bash
./mvnw -pl 52-StateConcurrencyAndConsistency test
```

测试验证：

1. 同 session 最大并发 Model call = 1。
2. 不同 session 可以产生真实重叠。
3. 两个 stale writer 只有一个 CAS 成功。
4. 同一 wakeup/webhook event id 只能 claim 一次。

## 10. 和前面课程的关系

```text
05 MultiUserConcurrency
10 ContextAndStateStore
23 DistributedStateAndStorage
38 DistributedBackends / CAS
47 AsyncToolAndWakeup
          ↓
52 StateConcurrencyAndConsistency
```

第 52 课不是新增一个中间件，而是把这些能力组合成生产一致性模型。
