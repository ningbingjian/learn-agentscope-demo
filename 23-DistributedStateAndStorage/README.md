# 23-DistributedStateAndStorage：多副本 Agent 的状态与工作区共享

## 1. 为什么单机正确，多 Pod 可能立刻出问题

假设已经把第 22 课部署成三个副本：

```text
        Load Balancer
        /     |      \
      Pod-A  Pod-B   Pod-C
```

第一轮请求落到 A，第二轮可能落到 B。

如果状态只在 A 的 JVM 或磁盘：

```text
第一轮：A 记住“我叫 Nick”
第二轮：B 收到“我叫什么？”
                  ↓
                不知道
```

所以生产 Agent 必须区分：

```text
计算实例
≠
持久状态
```

---

## 2. 本节目标

学完应能解释：

- `DistributedStore` 为什么是一站式存储入口；
- `AgentStateStore`、`BaseStore`、`SandboxSnapshotSpec`、`SandboxExecutionGuard` 分别负责什么；
- 为什么 `.distributedStore(store)` 不能替代 `.filesystem(...)`；
- `RemoteFilesystemSpec` 如何把 MEMORY、skills、sessions 等路径路由到共享 KV；
- `IsolationScope.USER` 和 `SESSION` 的差异；
- 为什么同一个用户跨 session 可以共享长期文件，但会话 AgentState 仍按 session 分开；
- Redis、MySQL、OSS 的能力差异；
- 两个应用副本如何共享同一个状态底座。

---

## 3. DistributedStore 能力图

```text
                DistributedStore
                      │
      ┌───────────────┼─────────────────┐
      ↓               ↓                 ↓
AgentStateStore    BaseStore      SandboxSnapshotSpec
      │               │                 │
会话状态          Workspace KV       沙箱快照
      │               │
context           MEMORY.md
summary           memory/
permission        skills/
plan state        sessions/
...
                      │
                      └──── SandboxExecutionGuard
                                ↓
                             分布式锁
```

AgentScope 2.0.1 的设计是：

```java
HarnessAgent.builder()
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
        .isolationScope(IsolationScope.USER))
```

`distributedStore` 提供后端组件，`filesystem` 决定文件访问模式，两者不是一个概念。

---

## 4. 为什么案例用 InMemory 组件

生产通常会用：

```text
Redis
MySQL/JDBC
OSS
```

但本课第一目标是看懂架构，所以默认使用：

```java
new InMemoryAgentStateStore()
new InMemoryStore()
```

再通过：

```java
DistributedStore.builder()
```

组合起来。

它们不是生产分布式后端，但接口和生产扩展完全一致，可以在没有 Redis/MySQL 的机器上完成所有结构实验。

---

## 5. 一步步编码

### Step 1：创建 StateStore 与 BaseStore

```java
@Bean
InMemoryAgentStateStore stateStore() {
    return new InMemoryAgentStateStore();
}

@Bean
InMemoryStore baseStore() {
    return new InMemoryStore();
}
```

### Step 2：组合 DistributedStore

```java
DistributedStore.builder()
    .agentStateStore(stateStore)
    .baseStore(baseStore)
    .build();
```

如果没有显式设置：

```text
SandboxSnapshotSpec
SandboxExecutionGuard
```

会使用默认 Noop 实现。

### Step 3：把 Store 注入 HarnessAgent

```java
HarnessAgent.builder()
    .name("distributed-agent")
    .model(model)
    .workspace(Paths.get(".agentscope/workspace"))
    .distributedStore(store)
    .filesystem(new RemoteFilesystemSpec()
        .isolationScope(IsolationScope.USER))
    .build();
```

### Step 4：理解 RemoteFilesystemSpec

不是所有文件都简单写本机。

共享路径包含：

```text
AGENTS.md
MEMORY.md
memory/
skills/
subagents/
knowledge/
plans/
agents/<agentId>/sessions/
agents/<agentId>/tasks/
```

这些会进入 BaseStore。

普通未管理文件仍可以由本地层处理。

---

## 6. USER scope 到底共享什么

假设：

```text
alice/session-a
alice/session-b
bob/session-a
```

`IsolationScope.USER` 下工作区命名空间类似：

```text
alice/session-a ─┐
                 ├─ agents/distributed-agent/users/alice/...
alice/session-b ─┘

bob/session-a ───── agents/distributed-agent/users/bob/...
```

所以 Alice 两个 session 可以共享：

```text
MEMORY.md
memory/
skills/
```

但 AgentState 依然通过 `(userId, sessionId)` 定位：

```text
alice/session-a AgentState
alice/session-b AgentState
```

不要混淆“Workspace isolation scope”和“AgentState session slot”。

---

## 7. REST 实验

### 对话

```bash
curl -X POST http://localhost:18081/api/distributed/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"session-a",
    "message":"记住：我的项目代号是 Phoenix"
  }'
```

### 写共享笔记

```bash
curl -X POST http://localhost:18081/api/distributed/note \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"session-a",
    "content":"Phoenix 使用多副本部署"
  }'
```

### 用 Alice 的另一个 session 读取

```bash
curl 'http://localhost:18081/api/distributed/inspect?userId=alice&sessionId=session-b'
```

因为本案例使用 USER scope，`session-b` 可以看到 Alice 写入共享 `memory/shared-note.md` 的内容。

---

## 8. 自动化测试：模拟两个 Pod

测试会构造：

```text
Replica A workspace = target/replica-a
Replica B workspace = target/replica-b
```

两者共用：

```text
InMemoryAgentStateStore
InMemoryStore
DistributedStore
```

先让 Replica A 调用同一个 session：

```text
alice/shared-session
```

然后直接从 Replica B 读取同一个 AgentState，验证能看到 A 写入的会话内容。

再让 A 写：

```text
memory/shared-note.md
```

B 用 Alice 的另一个 session 读取，验证 USER scope 的共享文件行为。

这就是一个最小“两个 Pod + 共享后端”的模拟。

---

## 9. 换成 Redis 时会发生什么

生产中可以把组合替换成：

```java
DistributedStore store = RedisDistributedStore.fromJedis(jedis);
```

Harness 其他主要代码不需要重写。

Redis 在 2.0.1 中覆盖最完整，适合：

```text
AgentState
Workspace KV
Sandbox snapshot
Sandbox distributed lock
```

MySQL/JDBC 更适合已有关系数据库体系的团队。

OSS 适合大容量快照/对象存储，但不适合承担分布式锁。

---

## 10. 显式配置优先级

需要记住：

```text
显式 builder 配置
        >
distributedStore 自动注入
        >
本地默认实现
```

例如显式 `.stateStore(...)` 可以覆盖 DistributedStore 提供的 stateStore。

---

## 11. 分布式场景真正需要关注什么

不仅是“把 Redis 接上”。

至少包括：

```text
状态一致性
CAS / version
多副本 session 路由
workspace namespace
锁
快照
TTL
故障恢复
跨节点消息
异步任务状态
```

`DistributedStore` 的价值是把这些基础设施入口统一起来。

---

## 12. 本节边界

本课不启动真实 Redis/MySQL，也不讲数据库部署运维。

重点是先把：

```text
HarnessAgent
      ↓
DistributedStore
      ↓
State + Workspace + Snapshot + Lock
```

这条架构链学清楚。

下一节进入：

```text
24-ObservabilityAndTracing
```

因为一个多副本 Agent 系统如果不可观测，出了问题基本无法定位。
