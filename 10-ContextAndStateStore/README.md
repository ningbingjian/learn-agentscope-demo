# 10-ContextAndStateStore

本节系统学习 AgentScope Java 2.x 中三个经常一起出现、但职责完全不同的概念：

```text
RuntimeContext -> 定位这次调用属于谁
AgentState     -> 保存这个会话当前有哪些运行状态
AgentStateStore-> 决定这些状态存到哪里
```

前面的第 02、05、06、08 节已经多次用到它们，本节不再把它们当作背景能力，而是单独拆开验证。

## 学习目标

完成本节后，你应该能够解释：

- `RuntimeContext` 为什么不是“记忆本身”。
- `(userId, sessionId)` 如何定位一份 `AgentState`。
- `AgentState` 中为什么不仅有聊天消息，还有 summary、permission/tool/task 等运行状态。
- `AgentStateStore` 负责什么、不负责什么。
- `InMemoryAgentStateStore` 与 `JsonFileAgentStateStore` 的差异。
- 为什么“应用重启后能恢复会话”取决于 Store，而不是 LLM。
- 单机文件存储与生产分布式存储的边界。

## 一、三个概念先分开

### RuntimeContext：一次调用的定位信息

```java
RuntimeContext context = RuntimeContext.builder()
        .userId("alice")
        .sessionId("session-1")
        .build();
```

可以把它理解成调用时携带的地址：

```text
这次请求
  userId    = alice
  sessionId = session-1
        |
        v
找到 alice/session-1 对应的状态槽位
```

它本身不是持久化状态。一次调用结束后，这个 `RuntimeContext` 对象没有必要保存。

### AgentState：这个槽位里真正保存的状态

`AgentState` 是一个会话的可恢复运行状态。除了对话上下文，还包含框架运行所需的其他信息。

可以概念化为：

```text
AgentState
├── userId
├── sessionId
├── context              对话消息
├── summary              上下文摘要
├── permissionContext    权限状态
├── toolContext          Tool 状态
├── tasksContext         Task / SubAgent 相关状态
└── planModeContext      Plan Mode 状态
```

因此不要把 `AgentState` 简化理解成 `List<Msg>`。

### AgentStateStore：状态放在哪里

`AgentStateStore` 是存储抽象。

```text
ReActAgent
   |
   | load/save AgentState
   v
AgentStateStore
   |
   +-- InMemoryAgentStateStore
   +-- JsonFileAgentStateStore
   +-- 生产环境可扩展 Redis / DB 等后端
```

本节只比较 AgentScope Core 自带、最容易观察的两种实现。

## 二、本节为什么同时创建两个 Agent

为了让差异一眼可见，本模块创建两个配置几乎相同的 `ReActAgent`：

```text
inMemoryAgent
    |
    +-- InMemoryAgentStateStore

fileAgent
    |
    +-- JsonFileAgentStateStore(.agentscope/state)
```

模型、系统提示词和请求格式都相同，唯一主要区别就是 StateStore。

这样就能把实验结果归因到“状态存储策略”，而不是 Prompt 或模型差异。

## 三、一步步把案例编码出来

### 第 1 步：创建 InMemory Store

```java
@Bean("inMemoryStateStore")
InMemoryAgentStateStore inMemoryStateStore() {
    return new InMemoryAgentStateStore();
}
```

它使用 JVM 内存保存状态。

特点：

- 快。
- 测试简单。
- JVM 退出后全部消失。
- 不适合多实例共享会话。

### 第 2 步：创建 JSON 文件 Store

```java
@Bean("jsonFileStateStore")
JsonFileAgentStateStore jsonFileStateStore() {
    return new JsonFileAgentStateStore(Paths.get(".agentscope/state"));
}
```

磁盘结构大致为：

```text
.agentscope/state/
└── alice/
    └── session-1/
        └── agent_state.json
```

应用重启不会删除这个文件，所以后续 Agent 可以重新加载。

### 第 3 步：把 Store 交给 ReActAgent

```java
return ReActAgent.builder()
        .name("file-state-agent")
        .sysPrompt("...")
        .model(model)
        .stateStore(stateStore)
        .build();
```

关键不是 Controller 手动执行 `save()`。

正常的 Agent 调用链是：

```text
agent.call(..., RuntimeContext)
        |
        v
根据 userId/sessionId 激活状态槽位
        |
        v
从 AgentStateStore 加载 AgentState
        |
        v
执行 ReAct
        |
        v
更新 AgentState
        |
        v
保存回 AgentStateStore
```

### 第 4 步：每次 HTTP 请求构造 RuntimeContext

```java
RuntimeContext context = RuntimeContext.builder()
        .userId(request.userId())
        .sessionId(request.sessionId())
        .build();

Msg reply = agent.call(new UserMessage(request.message()), context).block();
```

Controller 不需要维护：

```text
Map<sessionId, List<Message>>
```

也不需要自己拼历史消息。

### 第 5 步：观察当前 AgentState

调用完成后可以读取对应槽位：

```java
AgentState state = agent.getAgentState(context);
```

本案例把 `contextMessageCount` 和 `summary` 返回给客户端，帮助你观察状态正在变化。

### 第 6 步：绕过 Agent，直接看持久化结果

为了把“AgentState”与“AgentStateStore”再分开一层，接口：

```text
GET /api/state/file/{userId}/{sessionId}
```

直接调用：

```java
fileStateStore.get(
    userId,
    sessionId,
    "agent_state",
    AgentState.class
)
```

这一步没有调用模型，纯粹是在读取持久化状态。

## 四、启动

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"

./mvnw -pl 10-ContextAndStateStore spring-boot:run
```

端口仍为：

```text
18081
```

## 五、实验一：InMemory 会话在当前 JVM 内可以恢复

第一次请求：

```bash
curl -X POST http://localhost:18081/api/state/memory/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"memory-demo",
    "message":"请记住，我的项目代号是 Orion。"
  }'
```

第二次仍用同一个 user/session：

```bash
curl -X POST http://localhost:18081/api/state/memory/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"memory-demo",
    "message":"我的项目代号是什么？"
  }'
```

在 Spring Boot 没有重启的情况下，应该能够回答 `Orion`。

然后停止服务并重新启动，再问一次。

因为 Store 只在内存里，旧状态已经不存在。

## 六、实验二：JsonFile 重启后仍可恢复

先写入事实：

```bash
curl -X POST http://localhost:18081/api/state/file/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"file-demo",
    "message":"请记住，我最喜欢的数据库是 PostgreSQL。"
  }'
```

此时可以查看真正落盘的 AgentState：

```bash
curl http://localhost:18081/api/state/file/alice/file-demo
```

也可以直接查看目录：

```bash
find 10-ContextAndStateStore/.agentscope/state -type f
```

停止 Spring Boot，重新启动，再发送：

```bash
curl -X POST http://localhost:18081/api/state/file/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"file-demo",
    "message":"我最喜欢的数据库是什么？"
  }'
```

预期仍然能恢复之前的信息。

## 七、实验三：换 session 就是另一份 AgentState

保持 userId 不变：

```text
alice/file-demo
alice/another-session
```

它们对应两份独立状态。

同样：

```text
alice/shared
bob/shared
```

虽然 sessionId 相同，但 userId 不同，也属于不同槽位。

因此真正的会话地址始终是：

```text
(userId, sessionId)
```

## 八、为什么这和第 02 课不重复

第 02 课的问题是：

> 两次 HTTP 请求为什么能记住上文？

本节的问题是：

> 这份“记住的东西”在框架里究竟是什么对象，如何定位，又由谁保存到什么后端？

关注层次不同：

```text
02 SessionMemory
    -> 先建立“会话能恢复”的直觉

10 ContextAndStateStore
    -> 拆开 RuntimeContext / AgentState / AgentStateStore
```

## 九、生产环境怎么理解

`JsonFileAgentStateStore` 很适合：

- 本机学习。
- 单机开发。
- 观察真实状态文件。

但多实例部署时会出现：

```text
Pod A 本地文件 != Pod B 本地文件
```

因此生产环境需要一个所有实例都能访问的状态后端，或者配合明确的会话路由策略。

本节不展开 Redis/MySQL 实现细节，后续生产部署专题再讨论。

## 十、自动化测试

测试不调用 DashScope。

它直接验证 Store 契约：

```text
InMemory:
保存 -> 当前对象重新读取成功

JsonFile:
Store 实例 A 保存
        -> 重新 new Store 实例 B
        -> 从同一目录重新读取成功
```

运行：

```bash
./mvnw -pl 10-ContextAndStateStore test
```

## 十一、常见误区

### 误区 1：RuntimeContext 就是 Memory

不是。

RuntimeContext 是本次调用的上下文/定位信息；真正可恢复状态是 AgentState。

### 误区 2：有 sessionId 就自动持久化

不准确。

sessionId 只告诉 Agent 应该操作哪个槽位。能否跨 JVM 重启恢复，还取决于是否配置了持久化 StateStore。

### 误区 3：AgentState 只有聊天历史

不是。

随着 Permission、Tool、Task、Plan 等能力加入，AgentState 会承担更多运行状态。

### 误区 4：JsonFile 就适合 Kubernetes 多副本

通常不适合直接这样使用。Pod 本地磁盘天然不是共享状态中心。

## 十二、本节边界

本节只学习：

```text
RuntimeContext
AgentState
AgentStateStore
InMemoryAgentStateStore
JsonFileAgentStateStore
```

不展开：

- 长期记忆抽取。
- Context Compaction。
- Redis/MySQL 生产 Store。
- Workspace 文件体系。

这些会在后续课程继续学习。
