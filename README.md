# learn-agentscope-demo

用于跟随 AgentScope Java 2.x 官方文档逐节学习的 Spring Boot 多模块项目。

## 模块结构

```text
learn-agentscope-demo
├── 01-HelloWorld      # 最小 ReActAgent 问答，端口 18081
├── 02-SessionMemory   # HarnessAgent 会话记忆，端口 18081
├── 03-ToolCalling     # ReActAgent 调用 Java 工具，端口 18081
├── 04-StreamingEvents # AgentEvent 通过 SSE 流式输出，端口 18081
├── 05-MultiUserConcurrency # 同会话串行、不同会话并行，端口 18081
├── 06-AgentInterrupt  # 按 userId + sessionId 精准中断会话，端口 18081
├── 07-StructuredOutput # Java 类型约束 Agent 最终结构化结果，端口 18081
├── 08-PermissionHITL # Tool 权限 ASK 与人工确认后恢复执行，端口 18081
├── 09-MiddlewareLifecycle # Middleware 五个生命周期入口，端口 18081
├── 10-ContextAndStateStore # RuntimeContext / AgentState / StateStore，端口 18081
├── 11-AgentObserve # observe() 注入消息但不触发推理，端口 18081
├── 12-HarnessWorkspace # WorkspaceManager 与 Harness 工作区文件，端口 18081
├── 13-HarnessMemory # Harness 两层长期记忆与 MemoryConfig，端口 18081
├── 14-ContextCompaction # summary + recent tail 上下文压缩，端口 18081
└── 15-ApplicationRAG # application-layer retrieval + Agent 回答，端口 18081
```

每个学习模块都是完整、可独立启动的 Spring Boot 服务，模块之间没有代码依赖。

## 环境要求

- JDK 17+（本机已有 JDK 21，推荐直接使用）
- Maven 3.9+（项目使用 Maven Wrapper，无需修改全局 Maven）
- DashScope API Key

## 配置 API Key

十五个模块统一从环境变量读取 DashScope API Key。启动任一需要真实模型的模块前执行：

```bash
export DASHSCOPE_API_KEY="你的 DashScope API Key"
```

环境变量只对当前终端会话生效，不会写入代码或提交到 GitHub。

## 编译

当前终端的默认 Java 是 11，先切换到本机已有的 JDK 21：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
```

## 01-HelloWorld

```bash
./mvnw -pl 01-HelloWorld spring-boot:run
```

```bash
curl -X POST http://localhost:18081/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"你好，请用一句话介绍 AgentScope"}'
```

详细说明见 [`01-HelloWorld/README.md`](01-HelloWorld/README.md)。

## 02-SessionMemory

```bash
./mvnw -pl 02-SessionMemory spring-boot:run
```

详细的两轮会话测试见 [`02-SessionMemory/README.md`](02-SessionMemory/README.md)。

## 03-ToolCalling

```bash
./mvnw -pl 03-ToolCalling spring-boot:run
```

工具调用和观察方式见 [`03-ToolCalling/README.md`](03-ToolCalling/README.md)。

## 04-StreamingEvents

```bash
./mvnw -pl 04-StreamingEvents spring-boot:run
```

SSE 事件流测试见 [`04-StreamingEvents/README.md`](04-StreamingEvents/README.md)。

## 05-MultiUserConcurrency

```bash
./mvnw -pl 05-MultiUserConcurrency spring-boot:run
```

多用户并发与会话串行化测试见
[`05-MultiUserConcurrency/README.md`](05-MultiUserConcurrency/README.md)。

## 06-AgentInterrupt

```bash
./mvnw -pl 06-AgentInterrupt spring-boot:run
```

按 `userId + sessionId` 精准中断指定会话、观察 `GenerateReason.INTERRUPTED`，以及 AgentScope Java 2.0.1 中 `HarnessAgent` 的 per-session interrupt API 差异，见
[`06-AgentInterrupt/README.md`](06-AgentInterrupt/README.md)。

## 07-StructuredOutput

```bash
./mvnw -pl 07-StructuredOutput spring-boot:run
```

使用 Java `record` 作为 Agent 最终结果契约，通过 `call(..., TicketAnalysis.class, context)` 和 `Msg#getStructuredData()` 直接得到强类型业务对象，见
[`07-StructuredOutput/README.md`](07-StructuredOutput/README.md)。

## 08-PermissionHITL

```bash
./mvnw -pl 08-PermissionHITL spring-boot:run
```

学习 Permission System 的 `ALLOW / DENY / ASK`，重点验证退款 Tool 命中 `ASK` 后返回 `PERMISSION_ASKING`，再通过 `ConfirmResult` 批准或拒绝并恢复同一会话，见
[`08-PermissionHITL/README.md`](08-PermissionHITL/README.md)。

## 09-MiddlewareLifecycle

```bash
./mvnw -pl 09-MiddlewareLifecycle spring-boot:run
```

实现 `MiddlewareBase`，观察并统计 `onAgent`、`onReasoning`、`onActing`、`onModelCall`、`onSystemPrompt` 五个生命周期入口及洋葱模型，见
[`09-MiddlewareLifecycle/README.md`](09-MiddlewareLifecycle/README.md)。

## 10-ContextAndStateStore

```bash
./mvnw -pl 10-ContextAndStateStore spring-boot:run
```

系统拆解 `RuntimeContext -> AgentState -> AgentStateStore`，并通过 `InMemoryAgentStateStore` 与 `JsonFileAgentStateStore` 对比 JVM 内状态和跨重启文件持久化，见
[`10-ContextAndStateStore/README.md`](10-ContextAndStateStore/README.md)。

## 11-AgentObserve

```bash
./mvnw -pl 11-AgentObserve spring-boot:run
```

使用 `ResearcherAgent -> WriterAgent.observe(...)` 验证消息可以注入另一个 Agent 的上下文而不触发模型推理，并说明 2.0.1 中 observe 默认状态槽位的边界，见
[`11-AgentObserve/README.md`](11-AgentObserve/README.md)。

## 12-HarnessWorkspace

```bash
./mvnw -pl 12-HarnessWorkspace spring-boot:run
```

正式学习 Harness Workspace 与 `WorkspaceManager`，直接读取 `AGENTS.md / MEMORY.md / KNOWLEDGE.md`、列出知识文件、写入学习笔记，并区分 Workspace 与 AgentState，见
[`12-HarnessWorkspace/README.md`](12-HarnessWorkspace/README.md)。

## 13-HarnessMemory

```bash
./mvnw -pl 13-HarnessMemory spring-boot:run
```

学习 AgentScope Java 2.0.1 Harness 当前的两层长期记忆：`memory/YYYY-MM-DD.md` 每日流水与 `MEMORY.md` 策划后长期记忆，并通过 `MemoryConfig` 观察 flush、consolidation、retention 等配置。旧 Core `LongTermMemory` API 已 deprecated/forRemoval，本节不继续使用它。见
[`13-HarnessMemory/README.md`](13-HarnessMemory/README.md)。

## 14-ContextCompaction

```bash
./mvnw -pl 14-ContextCompaction spring-boot:run
```

使用 `CompactionConfig` 和 `ConversationCompactor` 学习长会话的 `old prefix -> summary + recent tail` 压缩过程，实验中关闭长期记忆 hooks 以隔离观察 compaction，并用 FakeModel 自动化测试真实压缩结果，见
[`14-ContextCompaction/README.md`](14-ContextCompaction/README.md)。

## 15-ApplicationRAG

```bash
./mvnw -pl 15-ApplicationRAG spring-boot:run
```

AgentScope Java 2.0.1 的旧 `GenericRAGHook` 已 deprecated/forRemoval，本节按照当前边界实现 application-layer RAG：应用层负责 Query -> Retrieve -> Context Injection，`ReActAgent` 负责基于检索上下文生成答案，并返回 sources，见
[`15-ApplicationRAG/README.md`](15-ApplicationRAG/README.md)。
