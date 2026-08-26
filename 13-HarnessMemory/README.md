# 13-HarnessMemory

本节学习 AgentScope Java 2.0.1 **当前真正使用的长期记忆机制：Harness Memory**。

> 先纠正一个容易踩坑的点：`io.agentscope.core.memory.LongTermMemory` 在 2.0.1 中已经标记为 `@Deprecated(forRemoval = true)`。2.0 的 Harness 长期记忆主要通过 Workspace 中的 `memory/YYYY-MM-DD.md` 与 `MEMORY.md` 管线实现，而不是继续围绕旧 `LongTermMemory` 接口扩展。

## 学习目标

完成本节后，你应该能够理解：

- 会话上下文 `AgentState.context` 与长期记忆不是同一个东西。
- Harness 的两层长期记忆为什么同时存在。
- `memory/YYYY-MM-DD.md` 与 `MEMORY.md` 分别负责什么。
- Flush、Consolidation、Compaction summary 三类 LLM 调用为什么不能混为一谈。
- `MemoryConfig` 控制哪些行为。
- 为什么 memory flush 是异步的，HTTP 回复完成后记忆文件可能稍后才出现。
- 为什么本节主动关闭 Compaction。

## 1. 为什么第 13 课不继续使用旧 LongTermMemory API

AgentScope 2.0.1 仍保留旧接口用于兼容，但源码已经明确标记为待删除。它的 javadoc 指出：conversation context 已经进入 `AgentState`，跨 session 的持久化不应再依赖旧接口。

与此同时，Harness 提供了新的文件化长期记忆管线：

```text
conversation
     |
     | Flush LLM
     v
memory/YYYY-MM-DD.md
     |
     | Consolidation LLM
     v
MEMORY.md
     |
     | 每轮推理注入
     v
system prompt
```

所以本课直接学习 Harness Memory。

## 2. Session Memory 与 Long-Term Memory 的区别

### Session Memory

前面的课程已经使用：

```text
RuntimeContext(userId, sessionId)
        ↓
AgentState.context
        ↓
当前会话历史
```

它回答的问题是：

> 当前这个 session 之前说了什么？

### Harness Long-Term Memory

它回答的是：

> 跨越多个 session 后，哪些稳定事实仍值得长期保留？

例如：

```text
用户：以后 Java 示例都默认 JDK 21。
```

这类长期偏好不应该只绑定在某一个聊天窗口。

## 3. Harness 的两层记忆

### 第一层：Daily Ledger

```text
memory/2026-08-26.md
```

特点：

- 按天追加；
- 更接近原始长期事实流水；
- 不负责全局去重；
- 由 Flush 过程产生。

### 第二层：Curated Memory

```text
MEMORY.md
```

特点：

- 是整理后的长期记忆；
- 会合并、去重、压缩 daily ledger；
- 每轮推理时会作为长期上下文注入；
- 由 Consolidation 过程维护。

可以理解为：

```text
Daily Memory = 日志
MEMORY.md    = 长期知识摘要
```

## 4. 三类 LLM 调用不要混淆

| 机制 | 输入 | 输出 | 配置入口 |
| --- | --- | --- | --- |
| Flush | 当前对话 | `memory/YYYY-MM-DD.md` | `MemoryConfig` |
| Consolidation | 多日日志 | `MEMORY.md` | `MemoryConfig` |
| Compaction | 当前长上下文前缀 | 当前 session summary | `CompactionConfig` |

本节只学习前两项。

第 14 课再专门学习 Compaction。

## 5. 本节为什么关闭 Compaction

配置中：

```java
HarnessAgent.builder()
        .memory(memoryConfig)
        .disableCompaction()
```

目的不是推荐生产关闭，而是为了实验隔离：

```text
第 13 课：只观察长期记忆
第 14 课：只观察上下文压缩
```

否则一次长会话中可能同时出现 memory flush 和 compaction，很难判断文件和状态变化到底来自哪个机制。

## 6. MemoryConfig

本模块显式创建：

```java
@Bean
MemoryConfig memoryConfig() {
    return MemoryConfig.builder()
            .flushTrigger(MemoryConfig.FlushTrigger.always())
            .consolidationMinGap(Duration.ofMinutes(30))
            .dailyFileRetentionDays(90)
            .sessionRetentionDays(180)
            .build();
}
```

### flushTrigger

支持：

```text
ALWAYS
NEVER
THROTTLED
```

本节使用 `ALWAYS`，让每次调用后都容易观察到 flush。

### consolidationMinGap

默认长期维护不应该每秒运行，因此 consolidation 有节流间隔。

### retention

Daily memory 和 session log 都需要生命周期治理，否则长期服务会无限增长。

## 7. 一次请求发生什么

```text
POST /api/memory/chat
      ↓
RuntimeContext(userId, sessionId)
      ↓
HarnessAgent.call(...)
      ↓
正常推理并返回响应
      ↓
响应流完成
      ↓
MemoryFlushMiddleware 异步触发
      ↓
LLM 提取长期事实
      ↓
memory/YYYY-MM-DD.md
      ↓
后台 maintenance 按节流条件运行 consolidation
      ↓
MEMORY.md
```

注意：flush 是异步启动的。

因此：

```text
/api/memory/chat 已经返回
```

并不意味着：

```text
memory/YYYY-MM-DD.md 已经立刻写完
```

真实实验时可以过几秒再查看 snapshot。

## 8. 项目结构

```text
13-HarnessMemory
├── .agentscope/workspace
│   ├── AGENTS.md
│   └── MEMORY.md
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/harnessmemory
    │   │   ├── HarnessMemoryApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   └── web/MemoryController.java
    │   └── resources/application.yml
    └── test/java/com/example/agentscope/harnessmemory
        └── MemoryConfigContractTest.java
```

## 9. 核心代码

### Agent

```java
return HarnessAgent.builder()
        .name("harness-memory-agent")
        .model(model)
        .workspace(Paths.get(".agentscope/workspace"))
        .memory(memoryConfig)
        .disableCompaction()
        .build();
```

### Chat

```java
RuntimeContext context = RuntimeContext.builder()
        .userId(request.userId())
        .sessionId(request.sessionId())
        .build();

Msg reply = agent.call(new UserMessage(request.message()), context).block();
```

### 直接观察长期记忆

```java
WorkspaceManager workspace = agent.workspaceFor(userId, sessionId);
String memory = workspace.readMemoryMd(context);
Path dailyDir = workspace.getMemoryDir(context);
```

这能让我们同时观察：

```text
MEMORY.md
memory/*.md
```

而不是只相信模型“好像记住了”。

## 10. 启动

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 13-HarnessMemory spring-boot:run
```

端口：`18081`。

## 11. 实验一：告诉 Agent 一个长期事实

```bash
curl -X POST http://localhost:18081/api/memory/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"session-a",
    "message":"请记住一个长期偏好：以后给我的 Java 示例默认使用 JDK 21。"
  }'
```

等待几秒，再查看：

```bash
curl 'http://localhost:18081/api/memory/snapshot?userId=alice&sessionId=session-a'
```

重点观察 `dailyMemoryFiles`。

## 12. 实验二：查看 MemoryConfig

```bash
curl http://localhost:18081/api/memory/config
```

你会看到类似：

```json
{
  "flushMode": "ALWAYS",
  "flushMinGap": "PT0S",
  "consolidationMinGap": "PT30M",
  "consolidationMaxTokens": 4000,
  "dailyFileRetentionDays": 90,
  "sessionRetentionDays": 180
}
```

## 13. 实验三：跨 session 的长期事实

先在 `session-a` 告诉 Agent 偏好，然后使用另一个 session：

```bash
curl -X POST http://localhost:18081/api/memory/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"session-b",
    "message":"我之前对 Java 示例的 JDK 版本有什么长期偏好？"
  }'
```

这里要注意两个层次：

1. daily ledger 只是事实流水；
2. 真正稳定注入每轮推理的是整理后的 `MEMORY.md`。

因此如果 consolidation 尚未发生，新的 session 不一定立刻使用刚刚写入 daily ledger 的事实。这正是“两层记忆”设计的意义。

## 14. 自动化测试

测试不调用 DashScope，验证 `MemoryConfig` 的契约：

```bash
./mvnw -pl 13-HarnessMemory test
```

覆盖：

- ALWAYS / NEVER / THROTTLED；
- throttled 的最小间隔；
- consolidation prompt `%d` 占位符校验；
- retention 和 token 配置。

## 15. 常见误区

### 误区 1：MEMORY.md 就是 AgentState

不是。

```text
AgentState.context -> 当前 session 对话状态
MEMORY.md          -> Harness 长期记忆文件
```

### 误区 2：每句话都会进入长期记忆

不是。Flush 的作用就是从对话里提炼值得长期保留的信息。

### 误区 3：daily memory 一写入，所有 session 立即都看到

不一定。真正稳定注入的是 curated `MEMORY.md`，daily ledger 需要 consolidation。

### 误区 4：LongTermMemory 接口仍是 2.0 推荐方案

不是。该 Core API 已经 deprecated/forRemoval。

## 16. 本节边界

本节不展开：

- Compaction；
- 大工具结果 eviction；
- RAG；
- Memory Search Tool 的复杂检索行为；
- 分布式 Workspace 后端。

下一节专门学习 `CompactionConfig + ConversationCompactor`。
