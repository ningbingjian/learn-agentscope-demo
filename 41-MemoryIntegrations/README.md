# 41-MemoryIntegrations

本课学习 AgentScope Java 2.0.1 官方提供的外部 Memory Integration：Mem0、ReMe、Bailian。

但本课首先要建立一个非常重要的版本意识：**这些 Integration 在 2.0.1 中仍然存在，但它们共同实现的 Core `LongTermMemory` 接口已经从 2.0.0 开始标记为 `@Deprecated(forRemoval = true)`。**

因此这不是一节“推荐你在新项目里继续大量使用旧 LongTermMemory API”的课，而是一节：

> 看懂官方现存集成、理解三种记忆服务差异，同时知道当前 AgentScope 2.x 推荐的状态/记忆边界。

---

## 1. 本课学习目标

完成后应该能回答：

1. Memory 和 RAG 有什么根本区别？
2. Mem0、ReMe、Bailian 分别解决什么问题？
3. 三种 Integration 为什么可以统一到同一接口？
4. `record(List<Msg>)` 和 `retrieve(Msg)` 分别什么时候发生？
5. Mem0 如何做 agent/user/run/metadata 隔离？
6. ReMe 的 trajectory 和 workspace 是什么？
7. Bailian 的 rerank/judge/rewrite 是什么？
8. 为什么 2.0.1 Integration 文档仍有 LongTermMemory，但 Core 又标记 deprecated？
9. 新系统应该优先选择 Harness Memory、AgentState/application layer，还是这些 legacy extensions？

---

## 2. 三种官方 Integration

| Provider | Artifact | 实现类 | 特点 |
| --- | --- | --- | --- |
| Mem0 | `agentscope-extensions-mem0` | `Mem0LongTermMemory` | 通用语义记忆，metadata filter 强 |
| ReMe | `agentscope-extensions-reme` | `ReMeLongTermMemory` | trajectory 摘要，workspace 隔离 |
| Bailian | `agentscope-extensions-memory-bailian` | `BailianLongTermMemory` | 百炼托管，rerank/judge/rewrite |

它们共同实现：

```java
io.agentscope.core.memory.LongTermMemory
```

接口只有两类核心动作：

```text
record(messages)
= 把值得长期保存的信息写出去

retrieve(message)
= 用当前问题找过去相关记忆
```

---

## 3. 必须先理解的版本边界

2.0.1 源码上的 `LongTermMemory`：

```java
@Deprecated(forRemoval = true, since = "2.0.0")
public interface LongTermMemory {
    Mono<Void> record(List<Msg> msgs);
    Mono<String> retrieve(Msg msg);
}
```

源码给出的当前建议是：

```text
conversation context
→ AgentState.getContext()

cross-session persistence
→ application layer
```

与此同时 2.0.1 Integration 文档仍保留 Mem0/ReMe/Bailian 适配器。

这意味着学习时要分成两层：

```text
“2.0.1 还能不能看懂/使用这些扩展？”
→ 能

“新生产架构是不是应该无脑继续绑定 deprecated LongTermMemory？”
→ 不建议
```

---

## 4. 和第 13 课 Harness Memory 的区别

第 13 课学习的是当前 Harness 模型：

```text
workspace/
├── MEMORY.md
└── memory/
    └── YYYY-MM-DD.md
```

这一套由 Harness hooks/tools 管理。

第 41 课讲的是另一条老 Core Integration 路线：

```text
ReActAgent
   ↓
LongTermMemory
   ↓
Mem0 / ReMe / Bailian
```

不要把两套机制理解成同一个 API。

推荐心智模型：

```text
Harness Agent 长期经验
→ Harness Memory / Skills / Workspace

普通业务 Agent 的跨会话用户画像
→ application-layer memory service

维护旧系统或研究现有官方 adapter
→ LongTermMemory integrations
```

---

## 5. Mem0

### 5.1 核心特点

Mem0 特别适合：

```text
用户偏好
历史事实
项目上下文
多租户 metadata filter
```

Builder：

```java
Mem0LongTermMemory memory = Mem0LongTermMemory.builder()
    .agentName("travel-agent")
    .userId("alice")
    .runName("trip-2026")
    .apiBaseUrl("http://localhost:8000")
    .apiType(Mem0ApiType.SELF_HOSTED)
    .build();
```

### 5.2 三层 ID

```text
agentName
= 哪个 Agent

userId
= 哪个用户

runName
= 哪次任务/运行
```

三个至少提供一个。

本课测试专门验证：全部不传时 `build()` 抛 `IllegalArgumentException`。

### 5.3 metadata

```java
.metadata(Map.of(
    "tenant", "company-a",
    "project", "agent-platform"
))
```

它既参与写入，也参与 retrieval filter。

---

## 6. ReMe

ReMe 的思路与 Mem0 不完全一样。

核心概念：

```text
conversation trajectory
        ↓
ReMe Server
        ↓
LLM extraction
        ↓
long-term memories
```

Builder 很简单：

```java
ReMeLongTermMemory.builder()
    .userId("tenant-a:project-1")
    .apiBaseUrl("http://localhost:8002")
    .build();
```

其中：

```text
userId
→ ReMe workspace_id
```

ReMe 没有 Mem0 那么灵活的 metadata filter，因此常见做法是把 namespace 编码到 userId。

---

## 7. Bailian Memory

百炼适合已经在阿里云生态里的系统。

```java
BailianLongTermMemory.builder()
    .apiKey(apiKey)
    .userId("alice")
    .memoryLibraryId("library-id")
    .projectId("project-id")
    .topK(20)
    .minScore(0.4)
    .enableRerank(true)
    .enableJudge(true)
    .enableRewrite(true)
    .build();
```

三个增强开关：

```text
rerank
= 对召回结果二次重排

judge
= 让模型进一步判断结果是否相关

rewrite
= 写入时对记忆做改写/合并
```

它们通常会增加延迟和调用费用，因此不是越多越好。

---

## 8. 消息过滤

ReMe/Bailian 文档明确说明它们不会把所有内部消息都写入长期记忆。

常见过滤：

```text
USER              ✅
ASSISTANT natural ✅
ToolUse request   ❌
compressed history ❌
```

原因很简单：

```text
工具调用 JSON
压缩历史摘要
框架内部消息
```

不等于用户真正值得长期记住的事实。

---

## 9. Memory 与 RAG

### RAG

```text
query:
“AgentScope 的 Plan Mode 怎么用？”

source:
产品文档/知识库
```

### Memory

```text
query:
“我上次说最喜欢什么部署方式？”

source:
这个用户过去的交互
```

所以：

```text
RAG = world/company knowledge
Memory = user/history knowledge
```

实际系统经常同时存在。

---

## 10. 当前推荐架构

对于全新 AgentScope 2.x 项目，本课更建议：

```text
Runtime conversation
        ↓
AgentState.context

Harness experience
        ↓
MEMORY.md / daily memory / Skills

Cross-session business memory
        ↓
Application Memory Service
        ↓
Mem0 / ReMe / Bailian REST API
```

也就是：可以继续使用这些后端服务，但不一定要把应用强绑定到已经 `forRemoval` 的 Core `LongTermMemory` 接口。

一种更容易长期维护的方式：

```java
interface UserMemoryService {
    void record(...);
    List<MemoryHit> search(...);
}
```

然后 Adapter：

```text
Mem0UserMemoryService
ReMeUserMemoryService
BailianUserMemoryService
```

AgentScope 只消费你自己的业务接口。

---

## 11. 本课代码为什么只构造，不访问远端

外部记忆服务的网络测试会受：

```text
API Key
容器
网络
账户
数据状态
```

影响。

因此本模块的 contract test 关注：

```text
三种官方实现真实进入 classpath
        ↓
真实 Builder 构造
        ↓
实现统一接口
        ↓
验证 Mem0 builder invariant
        ↓
验证 LongTermMemory deprecated-for-removal 状态
```

真正的 E2E 应该单独放 integration-test profile。

---

## 12. 启动

```bash
./mvnw -pl 41-MemoryIntegrations spring-boot:run
```

查看 Provider：

```bash
curl http://localhost:18081/api/memory/providers
```

查看 2.0.1 核心接口状态：

```bash
curl http://localhost:18081/api/memory/contract
```

构造三个真实官方实现（不调用远端 API）：

```bash
curl http://localhost:18081/api/memory/build-samples
```

---

## 13. 测试

```bash
./mvnw -pl 41-MemoryIntegrations test
```

测试重点不是“Mem0 云服务是否在线”，而是版本契约和集成边界。

---

## 14. 建议实验

1. 本地启动 Mem0 self-hosted，再调用 `record/retrieve`。
2. 本地启动 ReMe，观察 trajectory 写入。
3. 对比 Mem0 metadata 与 ReMe workspace 隔离。
4. 有百炼账号时开启 rerank/judge 对比延迟。
5. 自己实现 application-layer `UserMemoryService`，把 AgentScope deprecated API 隔离掉。
6. 对比第 13 课 Harness Memory，看哪类信息应该进哪套存储。

---

## 15. 本课结论

不要只记“AgentScope 支持 Mem0”。

真正要记住：

```text
Memory provider
只是存储/检索实现

Memory ownership
才决定系统架构
```

并且在 AgentScope Java 2.0.1：

```text
官方 integration adapter 仍存在
        +
Core LongTermMemory 已 forRemoval
```

写新系统时必须同时知道这两个事实。
