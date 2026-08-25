# 02-SessionMemory

本节通过两次独立的 HTTP 请求，学习 AgentScope 如何根据 `userId` 和 `sessionId` 保存、恢复并隔离会话状态。

```text
第一次请求 -> 保存事实 -> AgentStateStore 持久化
第二次请求 -> 恢复同一会话 -> 根据上文回答
```

## 学习目标

完成本节后，你应该能够理解：

- 会话记忆不是模型本身永久记住了用户输入。
- `RuntimeContext` 如何标识本次调用所属的用户和会话。
- AgentScope 如何根据 `(userId, sessionId)` 定位 `AgentState`。
- 为什么一个 Spring 单例 `HarnessAgent` 可以服务多个会话。
- `RuntimeContext`、`AgentState` 和 Workspace 之间的区别。

## 理论基础

### 模型并没有“记住”上一次 HTTP 请求

大模型每次推理只能看到当前传入的消息。第一次 HTTP 请求结束后，如果应用不保存对话历史，第二次请求中的模型就不知道之前说过什么。

所谓“会话记忆”，实际上是应用完成了下面的工作：

```text
接收新消息
    ↓
找到该会话的历史状态
    ↓
将历史 + 新消息一起交给模型
    ↓
保存新的用户消息和 Agent 回复
```

因此，记忆属于 Agent 应用的状态管理能力，不是模型自己的长期记忆。

### userId 和 sessionId 共同确定一个会话

AgentScope 通过二元组 `(userId, sessionId)` 定位状态：

| 调用 | `userId` | `sessionId` | 结果 |
| --- | --- | --- | --- |
| A | `alice` | `demo-session` | 创建或恢复 Alice 的 demo 会话 |
| B | `alice` | `demo-session` | 与 A 是同一会话，可恢复上文 |
| C | `alice` | `new-session` | 不同会话，与 A 隔离 |
| D | `bob` | `demo-session` | 不同用户，与 A 隔离 |

只有两个 ID 都相同，才会恢复同一份状态。

### RuntimeContext、AgentState 和 Workspace

这三个概念都与运行状态有关，但职责不同：

| 概念 | 本节中的作用 | 是否自动持久化 |
| --- | --- | --- |
| `RuntimeContext` | 携带本次调用的 `userId` 和 `sessionId` | 否，它是一次调用的临时上下文 |
| `AgentState` | 保存对话历史等 Agent 运行状态 | 是，通过 `AgentStateStore` 读写 |
| Workspace | 提供 `AGENTS.md`、工作文件和会话日志的文件系统空间 | 其中的文件会保留，但它不等于 `AgentState` |

`RuntimeContext` 只告诉 AgentScope “这次调用是谁的哪个会话”。AgentScope 再使用这两个 ID 从 `AgentStateStore` 读取对应的 `AgentState`。

本节未手动配置 `AgentStateStore`，`HarnessAgent` 会使用默认的 JSON 文件存储。它与 `.agentscope/workspace` 是两个不同的位置。

### 单例 Agent 不等于只有一份对话历史

`HarnessAgent` 在 Spring 中是单例 Bean：

```text
Spring 容器
└── HarnessAgent Bean
    ├── (alice, session-1) -> AgentState A
    ├── (alice, session-2) -> AgentState B
    └── (bob,   session-1) -> AgentState C
```

可复用的 Agent 定义（名称、模型、提示词和 Workspace）由单例 Bean 保持；每个会话的可变状态由 `(userId, sessionId)` 隔离。因此不需要每个用户都新建一个 Agent Bean。

### 本节为什么使用 HarnessAgent

| Agent | 定位 |
| --- | --- |
| `ReActAgent` | 核心推理 Agent，适合理解基本调用链 |
| `HarnessAgent` | 在推理能力之上整合 Workspace、记忆、任务等工程化能力 |

会话状态的核心机制是 `RuntimeContext + AgentStateStore`。本节使用 `HarnessAgent`，是为了同时展示一个更完整的 Agent 运行环境，但不会展开学习压缩、工具或子 Agent。

## 一次请求的完整过程

```text
POST /api/chat
      ↓
Controller 构建 RuntimeContext(userId, sessionId)
      ↓
HarnessAgent 按 (userId, sessionId) 加载 AgentState
      ↓
追加本次 UserMessage，调用模型生成回复
      ↓
将用户消息和 Agent 回复写回 AgentStateStore
      ↓
Controller 返回 JSON
```

如果同一个会话同时收到多次调用，AgentScope 会对该会话的状态操作进行协调；不同会话仍可以并行处理。

## 项目结构

```text
02-SessionMemory
├── .agentscope/workspace
│   └── AGENTS.md
├── pom.xml
└── src/main
    ├── java/com/example/agentscope/sessionmemory
    │   ├── SessionMemoryApplication.java
    │   ├── config/AgentConfiguration.java
    │   └── web/ChatController.java
    └── resources/application.yml
```

## 核心代码

### 1. 创建 HarnessAgent

```java
@Bean(destroyMethod = "close")
HarnessAgent noteTakerAgent(Model model) {
    return HarnessAgent.builder()
            .name("note-taker")
            .sysPrompt("你是一个帮助用户做笔记的助手。")
            .model(model)
            .workspace(Paths.get(".agentscope/workspace"))
            .build();
}
```

- `name` 用于标识这个 Agent，也会出现在状态和日志目录中。
- `model` 由 DashScope Spring Boot Starter 自动创建并注入。
- `workspace` 指向当前模块下的 `.agentscope/workspace`。
- `destroyMethod = "close"` 让 Spring 停止时释放 Agent 资源。

Workspace 中的 `AGENTS.md` 是一份可编辑的行为说明，本节把 Agent 设定成了简洁的学习笔记助手。

### 2. 为每次请求构建 RuntimeContext

```java
RuntimeContext context = RuntimeContext.builder()
        .userId(request.userId())
        .sessionId(request.sessionId())
        .build();
```

Controller 不保存对话历史，只负责传递会话的定位信息。真正的状态恢复和保存由 AgentScope 完成。

### 3. 调用 Agent

```java
Msg reply = agent.call(new UserMessage(request.message()), context).block();
```

`UserMessage` 表示本轮用户输入，`context` 确定它属于哪个会话。`call()` 返回 `Mono<Msg>`；本节仍使用 Spring MVC，因此用 `.block()` 等待回复。

## 请求参数

请求地址：

```text
POST http://localhost:18081/api/chat
```

| 参数 | 作用 |
| --- | --- |
| `userId` | 用户标识 |
| `sessionId` | 该用户下的会话标识 |
| `message` | 本轮用户消息 |

三个字段都不能为空。

## 启动

在项目根目录切换到 JDK 21：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

设置环境变量并单独启动当前模块：

```bash
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 02-SessionMemory spring-boot:run
```

## 测试会话恢复

先在会话中告诉 Agent 一个事实：

```bash
curl -X POST http://localhost:18081/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"demo-session",
    "message":"我叫天宇，今天准备一个关于 ReAct 的技术分享。"
  }'
```

再使用完全相同的 `userId` 和 `sessionId` 提问：

```bash
curl -X POST http://localhost:18081/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"demo-session",
    "message":"我叫什么？我今天要干什么？"
  }'
```

预期回复能说出“天宇”和“准备 ReAct 技术分享”。两次 HTTP 请求彼此独立，第二次仍能回答，说明会话状态已被恢复。

## 测试会话隔离

将 `sessionId` 换成一个新值：

```bash
curl -X POST http://localhost:18081/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"another-session",
    "message":"我叫什么？我今天要干什么？"
  }'
```

新会话中没有之前的上下文，Agent 应该说无法确定，而不是回答“天宇”。也可以保持 `sessionId` 不变、把 `userId` 换成 `bob`，结果同样应当隔离。

## 观察持久化结果

调用接口后，可以观察两类文件：

```text
~/.agentscope/state/note-taker/alice/demo-session/agent_state.json
02-SessionMemory/.agentscope/workspace/agents/note-taker/sessions/
```

- `agent_state.json` 是默认 `AgentStateStore` 保存的 Agent 状态，用于后续调用恢复上文。
- Workspace 下的 sessions 目录是会话日志，便于观察和追溯。

停止并重启 Spring Boot 后，再使用相同的两个 ID 调用，仍然应能恢复之前的会话。

## 本节边界

本节只学习会话状态的定位、持久化、恢复和隔离，不展开学习长对话压缩、长期记忆、工具调用或流式输出。

## 延伸阅读

- [AgentScope Java：运行时上下文与状态](https://java.agentscope.io/v2/zh/docs/building-blocks/context.html)
- [AgentScope Java：Harness 架构](https://java.agentscope.io/v2/zh/docs/harness/architecture.html)
- [AgentScope Java：Workspace](https://java.agentscope.io/v2/zh/docs/harness/workspace.html)
