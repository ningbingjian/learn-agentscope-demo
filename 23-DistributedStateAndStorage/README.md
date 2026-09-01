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
- 两个应用副本如何共享同一个状态底座；
- 为什么 AgentScope 2.0.1 会拒绝 `RemoteFilesystemSpec + InMemoryAgentStateStore` 这种伪分布式组合。

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

## 4. 为什么案例不直接使用 InMemoryAgentStateStore

生产通常会用：

```text
Redis
MySQL/JDBC
OSS
```

本课第一目标仍然是看懂架构，而且希望默认不依赖 Redis/MySQL 服务。但这里有一个非常重要的 AgentScope Java 2.0.1 运行时边界：

```text
RemoteFilesystemSpec
        +
InMemoryAgentStateStore / JsonFileAgentStateStore
        ↓
启动时直接拒绝
```

原因很合理：`RemoteFilesystemSpec` 表达的是多副本/共享后端语义，而内置 `InMemoryAgentStateStore` 和 `JsonFileAgentStateStore` 明确是单进程/本地实现。如果允许两者组合，代码表面像“分布式”，实际换 Pod 后会丢状态。

因此本课使用：

```java
new DemoSharedAgentStateStore()
new InMemoryStore()
```

其中 `DemoSharedAgentStateStore` **只是同一 JVM 内的教学 test double**：它实现 `AgentStateStore` 契约，并让两个独立 `HarnessAgent` 实例共享同一个 store 对象，用来观察 `DistributedStore` 的装配和跨 replica 恢复流程。

它不是生产分布式实现，也不能跨 JVM/Pod。生产必须替换为真实共享后端，例如 Redis/MySQL 对应的 AgentStateStore/DistributedStore。

这样既能在没有外部基础设施的机器上完成结构实验，也不会把框架明确标记为 local-only 的实现冒充生产分布式后端。

---

## 5. 一步步编码

### Step 1：创建教学 Shared StateStore 与 BaseStore

```java
@Bean
AgentStateStore sharedStateStore() {
    return new DemoSharedAgentStateStore();
}

@Bean
InMemoryStore sharedBaseStore() {
    return new InMemoryStore();
}
```

再次强调：

```text
DemoSharedAgentStateStore = same-JVM teaching double
InMemoryStore             = same-JVM teaching BaseStore
```

它们只是为了不启动 Redis/MySQL 时学习 API，不代表真正跨进程共享。

### Step 2：组合 DistributedStore

```java
DistributedStore.builder()
    .agentStateStore(sharedStateStore)
    .baseStore(sharedBaseStore)
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

这里如果把 `DemoSharedAgentStateStore` 换回内置 `InMemoryAgentStateStore`，AgentScope 2.0.1 会在 Builder 校验阶段拒绝该组合。这不是测试障碍，而是在帮助你阻止错误的生产拓扑。

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

注意：默认 Demo 的共享仅发生在当前应用进程中。真正启动两个独立 Pod 时，必须换成 Redis/MySQL 等共享后端。

---

## 8. 自动化测试：在同一 JVM 模拟两个 Replica

测试会构造：

```text
Replica A workspace = target/replica-a
Replica B workspace = target/replica-b
```

两者共用同一个：

```text
DemoSharedAgentStateStore
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

这个实验验证的是：

```text
两个独立 HarnessAgent 实例
        ↓
共享 DistributedStore 契约
        ↓
State / Workspace 可被另一个实例恢复
```

它**不是**“两个真实 Pod”的网络级集成测试。真实多 Pod 验证应把后端替换成 Redis/MySQL，并在两个 JVM/容器之间执行同样场景。

---

## 9. 换成 Redis 时会发生什么

生产中可以把教学组合替换成真实 Redis DistributedStore/AgentStateStore。

Harness 的调用模型和 `RemoteFilesystemSpec` 不需要因此重写，变化集中在基础设施后端。

Redis 在 2.0.1 中适合承载：

```text
AgentState
Workspace KV
Sandbox snapshot
Sandbox distributed lock
```

MySQL/JDBC 更适合已有关系数据库体系的团队。

OSS 适合大容量快照/对象存储，但不适合单独承担分布式锁。

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

但“优先级更高”不代表“组合一定合法”：选择 `RemoteFilesystemSpec` 时，有效 AgentStateStore 仍必须满足共享/分布式语义。

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

本课默认不启动真实 Redis/MySQL，也不讲数据库部署运维。

默认代码使用 same-JVM teaching double 学习：

```text
HarnessAgent
      ↓
DistributedStore
      ↓
State + Workspace + Snapshot + Lock
```

生产部署必须把教学 state/base store 换成真正跨进程共享的 Redis/MySQL 等实现；不要把 `DemoSharedAgentStateStore` 当作生产组件。

下一节进入：

```text
24-ObservabilityAndTracing
```

因为一个多副本 Agent 系统如果不可观测，出了问题基本无法定位。
