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
├── 15-ApplicationRAG # application-layer retrieval + Agent 回答，端口 18081
├── 16-SubAgentOrchestration # 主 Agent 委派、子 Agent 与后台任务，端口 18081
├── 17-PlanMode # 只读规划、PLAN.md 与阶段切换，端口 18081
├── 18-Skills # workspace Skills、SKILL.md 与按需加载，端口 18081
├── 19-MCPAndToolsConfig # MCP stdio 与 tools.json Tool Surface，端口 18081
├── 20-FilesystemAndSandbox # Local/Remote/Sandbox 与执行隔离，端口 18081
├── 21-GatewayAndChannel # ChatUiChannel、Gateway、SSE 与 SubAgent 直连，端口 18081
├── 22-AgentServiceAndDeployment # Agent Protocol 与服务部署，端口 18081
├── 23-DistributedStateAndStorage # DistributedStore 与多副本共享，端口 18081
├── 24-ObservabilityAndTracing # 日志、Metrics 与 OpenTelemetry Trace，端口 18081
├── 25-ExecutionResilience # Model/Tool 超时、重试与指数退避，端口 18081
├── 26-GracefulShutdownAndRecovery # Drain、SIGTERM 与状态恢复，端口 18081
├── 27-KubernetesProductionDeployment # 多副本、Probe、HPA、PDB 与优雅下线，端口 18081/18082
├── 28-MessageAndEventModel # Msg/ContentBlock 与 AgentEvent 生命周期，端口 18081
├── 29-ModelLayerAndRegistry # ModelRegistry、ModelCreationContext 与 Provider SPI，端口 18081
├── 30-AdvancedTooling # ToolGroup、Context 注入与 ToolEmitter，端口 18081
├── 31-ExternalToolAndHITL # 外部执行 Tool 的暂停、回填与恢复，端口 18081
├── 32-SkillMarketplaceAndSelfLearning # Skill 市场、自学习、审核与 Curator，端口 18081
├── 33-AdminOpsControlPlane # Admin Starter、Actuator 与运维控制面，端口 18081
├── 34-AGUIProtocol # AgentEvent 到 AG-UI 标准前端事件协议，端口 18081
├── 35-A2AProtocol # A2A Server、AgentCard、JSON-RPC 与 A2aAgent Client，端口 18081
├── 36-ChatCompletionsCompatibility # OpenAI Chat Completions 兼容接口，端口 18081
├── 37-MultiModelProviders # 多模型 Provider SPI 与 ModelRegistry 路由，端口 18081
├── 38-DistributedBackends # Redis/MySQL/OSS、JDBC CAS 与混合 DistributedStore，端口 18081
├── 39-SandboxProviders # Docker/K8s/E2B/Daytona/AgentRun Sandbox Provider，端口 18081
├── 40-RAGIntegrations # Simple/Dify/RAGFlow/Haystack/Bailian RAG 集成，端口 18081
├── 41-MemoryIntegrations # Mem0/ReMe/Bailian 与 LongTermMemory 版本边界，端口 18081
├── 42-SkillRepositoryBackends # Git/MySQL/PostgreSQL/Nacos Skill Repository，端口 18081
├── 43-EnterpriseChannels # DingTalk/Feishu/WeCom/GitHub/GitLab Channel，端口 18081
├── 44-EnterpriseInfrastructure # Scheduler/Nacos/Higress 企业基础设施，端口 18081
├── 45-StudioAndTraining # AgentScope Studio 与 Online Training，端口 18081
├── 46-AgentTeams # application-layer Team Board、CAS 与 mailbox，端口 18081
├── 47-AsyncToolAndWakeup # Async Tool、Registry、Inbox 与 Wakeup，端口 18081
├── 48-AdvancedPermissionAndSecurity # Permission Engine 与安全决策边界，端口 18081
├── 49-ModelRuntimeDeepDive # GenerateOptions、Formatter 与 Model Runtime，端口 18081
├── 50-HookAndRuntimeExtension # Middleware / Hook / Event 扩展机制，端口 18081
├── 51-ContextBudgetAndCompactionDeepDive # Context Budget、Compaction 与 Tool Result Eviction，端口 18081
├── 52-StateConcurrencyAndConsistency # Session 串行、跨副本 CAS 与幂等，端口 18081
├── 53-AgentTestingAndEvaluation # Agent 测试、评测指标与 Eval Gate，端口 18081
├── 54-AgentSecurityArchitecture # Untrusted Data、Tool Surface 与纵深安全，端口 18081
└── 55-ProductionAgentArchitecture # 生产级 Agent Architecture 综合项目，端口 18081
```

每个学习模块都是完整、可独立启动的 Spring Boot 服务，模块之间没有代码依赖。

## 环境要求

- JDK 17+（本机已有 JDK 21，推荐直接使用）
- Maven 3.9+（项目使用 Maven Wrapper，无需修改全局 Maven）
- DashScope API Key（仅需要真实 DashScope 模型的模块）

## 配置 API Key

需要真实 DashScope 模型的模块统一从环境变量读取 API Key：

```bash
export DASHSCOPE_API_KEY="你的 DashScope API Key"
```

`29-ModelLayerAndRegistry`、`31-ExternalToolAndHITL`、`32-SkillMarketplaceAndSelfLearning`、`33-AdminOpsControlPlane`、`34-AGUIProtocol`、`35-A2AProtocol`、`36-ChatCompletionsCompatibility`、`37-MultiModelProviders`、`38-DistributedBackends`、`39-SandboxProviders`、`40-RAGIntegrations`、`41-MemoryIntegrations`、`42-SkillRepositoryBackends`、`43-EnterpriseChannels`、`44-EnterpriseInfrastructure`、`45-StudioAndTraining` 的核心实验均不需要外部模型 API Key。第 40 课使用本地 deterministic Embedding + InMemoryStore，第 41 课只构造官方 Memory Adapter 并检查版本契约，第 42 课使用本地临时 Git 仓库做真实 clone/sync/read；第 43 课只测试 Channel Adapter 与公共防护层，第 44 课只构造调度配置和真实基础设施类型，第 45 课只 build TrainingRunner、不 start，也不初始化 Studio 网络连接。

第 46-55 课的核心实验也默认不依赖外部模型 API Key：课程使用 application-layer 协调、deterministic Model、H2/JdbcStore、真实 AgentScope Runtime 契约或本地 Eval/Security 组件来稳定验证行为；涉及生产模型、共享数据库、远程 Sandbox 等部分会在各模块 README 中明确标记为生产替换项。

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

## 16-SubAgentOrchestration

```bash
./mvnw -pl 16-SubAgentOrchestration spring-boot:run
```

通过 `workspace/subagents/*.md` 声明 `researcher` 与 `reviewer`，学习主 Agent 如何使用 `agent_spawn` 委派任务、同步/后台子任务、`task_id`、ISOLATED/SHARED Workspace 和持久子会话等概念，见
[`16-SubAgentOrchestration/README.md`](16-SubAgentOrchestration/README.md)。

## 17-PlanMode

```bash
./mvnw -pl 17-PlanMode spring-boot:run
```

开启 Harness Plan Mode，学习 `plan_enter / plan_write / plan_exit`、只读规划阶段、`plans/PLAN.md`、HITL 退出，以及通过业务代码按 session 进入/退出计划状态，见
[`17-PlanMode/README.md`](17-PlanMode/README.md)。

## 18-Skills

```bash
./mvnw -pl 18-Skills spring-boot:run
```

通过 `workspace/skills/<name>/SKILL.md` 提供 `java-code-review` 与 `api-design` 两个真实 Skill，学习 available skills、按需加载、references、Skill 与 Tool/SubAgent/Plan 的职责边界以及 Skill Repository 扩展，见
[`18-Skills/README.md`](18-Skills/README.md)。

## 19-MCPAndToolsConfig

```bash
./mvnw -pl 19-MCPAndToolsConfig spring-boot:run
```

学习 Harness `tools.json`、MCP stdio、`McpServerConfig`、`enableTools` 与最终 `allow/deny` ToolFilter；默认不启动 MCP 子进程，设置 `MCP_DEMO_ENABLED=true` 后连接仓库内置的最小 Python MCP Server，见
[`19-MCPAndToolsConfig/README.md`](19-MCPAndToolsConfig/README.md)。

## 20-FilesystemAndSandbox

```bash
./mvnw -pl 20-FilesystemAndSandbox spring-boot:run
```

学习 `LocalFilesystemSpec / RemoteFilesystemSpec / SandboxFilesystemSpec`、`LocalFsMode.ROOTED` 和 `IsolationScope`。默认使用本地受限模式，设置 `SANDBOX_DEMO_ENABLED=true` 后切换到 Docker Sandbox，见
[`20-FilesystemAndSandbox/README.md`](20-FilesystemAndSandbox/README.md)。

## 21-GatewayAndChannel

```bash
./mvnw -pl 21-GatewayAndChannel spring-boot:run
```

使用 `ChatUiChannel + Gateway + SendOptions` 将 HTTP 请求映射到稳定用户/session，提供普通与 SSE 接口，并演示 `SubagentExposedEvent` 与通过 `subagentId` 直接继续和暴露子 Agent 对话，见
[`21-GatewayAndChannel/README.md`](21-GatewayAndChannel/README.md)。

## 22-AgentServiceAndDeployment

```bash
./mvnw -pl 22-AgentServiceAndDeployment spring-boot:run
```

使用 `agentscope-extensions-agent-protocol` 将 `HarnessAgent` 自动暴露为 `/tasks` 标准任务服务，学习提交、状态、wait、cancel、SSE events、HITL resume、Remote SubAgent 托管以及 Docker 镜像部署，见
[`22-AgentServiceAndDeployment/README.md`](22-AgentServiceAndDeployment/README.md)。

## 23-DistributedStateAndStorage

```bash
./mvnw -pl 23-DistributedStateAndStorage spring-boot:run
```

学习 `DistributedStore` 如何统一 `AgentStateStore / BaseStore / SandboxSnapshotSpec / SandboxExecutionGuard`，并用两个不同 HarnessAgent 模拟两个 Pod，共享同一个 state/workspace 后端，见
[`23-DistributedStateAndStorage/README.md`](23-DistributedStateAndStorage/README.md)。

## 24-ObservabilityAndTracing

```bash
./mvnw -pl 24-ObservabilityAndTracing spring-boot:run
```

学习 Harness 默认 `AgentTraceMiddleware`、自定义聚合 Metrics Middleware，以及 `OtelTracingMiddleware` 的 `invoke_agent / chat / execute_tool` spans。设置 `OTEL_DEMO_ENABLED=true` 可用 LoggingSpanExporter 直接观察标准 OpenTelemetry span，见
[`24-ObservabilityAndTracing/README.md`](24-ObservabilityAndTracing/README.md)。

## 25-ExecutionResilience

```bash
./mvnw -pl 25-ExecutionResilience spring-boot:run
```

使用 `ExecutionConfig` 分别配置 Model 与 Tool 的 timeout、retry、exponential backoff 和 retry filter，并通过 `slow_task` 观察 Tool 超时。重点理解为什么 Model 可重试，而有副作用的 Tool 默认应避免自动 retry，见
[`25-ExecutionResilience/README.md`](25-ExecutionResilience/README.md)。

## 26-GracefulShutdownAndRecovery

```bash
./mvnw -pl 26-GracefulShutdownAndRecovery spring-boot:run
```

学习 `GracefulShutdownManager`、`GracefulShutdownConfig`、`PartialReasoningPolicy`、AgentScope JVM SIGTERM Hook 与 Admin `agentscope-drain` 端点，串起“停止接新请求 → 等待在途请求 → 超时中断/保存 → 下次恢复”的优雅下线链路，见
[`26-GracefulShutdownAndRecovery/README.md`](26-GracefulShutdownAndRecovery/README.md)。

## 27-KubernetesProductionDeployment

```bash
./mvnw -pl 27-KubernetesProductionDeployment spring-boot:run
```

把前面的生产能力组合成 Kubernetes 示例：本地可无 Redis 启动；`distributed` profile 下使用 `RedisDistributedStore` 共享状态和 Workspace，并提供独立 management 端口、startup/liveness/readiness probes、preStop Drain、SIGTERM graceful shutdown、RollingUpdate、HPA、PDB 与 Secret 示例。详细部署过程见
[`27-KubernetesProductionDeployment/README.md`](27-KubernetesProductionDeployment/README.md)。

## 28-MessageAndEventModel

```bash
./mvnw -pl 28-MessageAndEventModel spring-boot:run
```

系统学习 `Msg`、`ContentBlock`、`ToolUseBlock / ToolResultBlock` 与 `AgentEvent` 的 start/delta/end 生命周期，并通过普通接口与 SSE 对照“最终消息”和“增量事件”。见
[`28-MessageAndEventModel/README.md`](28-MessageAndEventModel/README.md)。

## 29-ModelLayerAndRegistry

```bash
./mvnw -pl 29-ModelLayerAndRegistry spring-boot:run
```

使用仓库内置 `LessonEchoModel` 和真正的 Java `ModelProvider SPI` 学习 `ModelRegistry` 解析顺序、`ModelCreationContext`、多租户模型配置与 `CachePolicy`。本模块无需 API Key。见
[`29-ModelLayerAndRegistry/README.md`](29-ModelLayerAndRegistry/README.md)。

## 30-AdvancedTooling

```bash
./mvnw -pl 30-AdvancedTooling spring-boot:run
```

学习 `ToolGroup / ToolGroupScope`、2.0.1 的 `reset_equipped_tools`、RuntimeContext 与自定义 POJO 自动注入，以及 `ToolEmitter` 的长任务进度流。见
[`30-AdvancedTooling/README.md`](30-AdvancedTooling/README.md)。

## 31-ExternalToolAndHITL

```bash
./mvnw -pl 31-ExternalToolAndHITL spring-boot:run
```

学习 `@Tool(externalTool = true)` 的暂停语义，区分 Permission ASK 与外部执行，验证 `TOOL_SUSPENDED`、同一 `toolCallId` 的 `ToolResultBlock` 回填以及同一 session 恢复。模块使用 deterministic Model，无需 API Key。见
[`31-ExternalToolAndHITL/README.md`](31-ExternalToolAndHITL/README.md)。

## 32-SkillMarketplaceAndSelfLearning

```bash
./mvnw -pl 32-SkillMarketplaceAndSelfLearning spring-boot:run
```

从“使用 Skill”继续推进到 Skill 治理：直接调用 Harness 真正的 `propose_skill` 生成草稿，通过 `SkillPromotionGate + promoteSkill()` 审核晋升，并学习 Marketplace、多层覆盖、Usage/Audit 和 `SkillCurator` 自学习治理闭环。无需外部模型 API Key。见
[`32-SkillMarketplaceAndSelfLearning/README.md`](32-SkillMarketplaceAndSelfLearning/README.md)。

## 33-AdminOpsControlPlane

```bash
./mvnw -pl 33-AdminOpsControlPlane spring-boot:run
```

学习 `agentscope-admin-spring-boot-starter` 的完整运维控制面：`status / agents / tools / models / usage / commands / permissions / subagents / doctor` Actuator endpoints、`/v1/admin` Session Data Plane，以及 `write-enabled + Admin Token` 写操作保护。模块默认只开启读控制面，并使用本地 deterministic Model。见
[`33-AdminOpsControlPlane/README.md`](33-AdminOpsControlPlane/README.md)。

## 34-AGUIProtocol

```bash
./mvnw -pl 34-AGUIProtocol spring-boot:run
```

学习 `AguiAgentAdapter + AguiAgentRegistry`，把 AgentScope 的 `AgentEvent` 映射为 AG-UI 的 `RUN_* / TEXT_MESSAGE_* / TOOL_CALL_* / interrupt-resume` 标准前端事件，并通过 Starter 自动开放 `/agui/run` SSE 接口。模块使用 deterministic Model，无需 API Key。见
[`34-AGUIProtocol/README.md`](34-AGUIProtocol/README.md)。

## 35-A2AProtocol

```bash
./mvnw -pl 35-A2AProtocol spring-boot:run
```

学习 A2A Server/Client：Spring Starter 从 `ReActAgent` 自动创建 `AgentScopeA2aServer`，开放 `/.well-known/agent-card.json` 与 JSON-RPC，同时使用 `A2aAgent` 将远端 A2A Agent 包装回统一 `Agent` 抽象。模块使用 deterministic Model，无需 API Key。见
[`35-A2AProtocol/README.md`](35-A2AProtocol/README.md)。

## 36-ChatCompletionsCompatibility

```bash
./mvnw -pl 36-ChatCompletionsCompatibility spring-boot:run
```

学习 `agentscope-chat-completions-web-starter` 将 ReActAgent 暴露为 OpenAI 风格 `/v1/chat/completions`，重点理解其 100% stateless、客户端持有完整 history、每请求 fresh prototype Agent，以及非流式 JSON / SSE stream / OpenAI Tool Schema 兼容。模块使用 deterministic Model，无需 API Key。见
[`36-ChatCompletionsCompatibility/README.md`](36-ChatCompletionsCompatibility/README.md)。

## 37-MultiModelProviders

```bash
./mvnw -pl 37-MultiModelProviders spring-boot:run
```

同时引入 AgentScope Java 2.0.1 的 OpenAI、DashScope、Gemini、Anthropic、Ollama 模型扩展，通过真实 `ServiceLoader<ModelProvider>` 与 `ModelRegistry.canResolve()` 观察 OpenAI / DeepSeek / Kimi / GLM / MiniMax / Qwen / Gemini / Claude / Ollama 的统一 Provider 路由。核心实验不创建模型、不访问外网、不需要 API Key。见
[`37-MultiModelProviders/README.md`](37-MultiModelProviders/README.md)。

## 38-DistributedBackends

```bash
./mvnw -pl 38-DistributedBackends spring-boot:run
```

深化 `DistributedStore`：对比 Redis / MySQL-JDBC / OSS 的 AgentStateStore、BaseStore、Snapshot、ExecutionGuard 能力，使用 H2 MySQL compatibility mode 真跑 `JdbcStore` 与 `putIfVersion` CAS，并演示 `DistributedStore.builder()` 混合不同组件。默认无需 Redis/MySQL/OSS 服务。见
[`38-DistributedBackends/README.md`](38-DistributedBackends/README.md)。

## 39-SandboxProviders

```bash
./mvnw -pl 39-SandboxProviders spring-boot:run
```

学习 Docker、Kubernetes agent-sandbox、E2B、Daytona、AgentRun 五种 `SandboxFilesystemSpec` Provider，理解它们如何保持同一套 Harness 文件/执行语义，并区分“Agent 服务部署到 K8s”和“Agent 在 K8s Sandbox 中执行代码”。默认只构造真实官方 Spec，不创建远端 Sandbox，因此无需云凭证。见
[`39-SandboxProviders/README.md`](39-SandboxProviders/README.md)。

## 40-RAGIntegrations

```bash
./mvnw -pl 40-RAGIntegrations spring-boot:run
```

使用真实 `SimpleKnowledge + EmbeddingModel + InMemoryStore + RetrieveConfig` 完成离线文档入库与向量检索，同时对比 Dify、RAGFlow、Haystack、Bailian 官方 Knowledge Adapter。默认使用 deterministic Embedding，不需要外部 API Key。见
[`40-RAGIntegrations/README.md`](40-RAGIntegrations/README.md)。

## 41-MemoryIntegrations

```bash
./mvnw -pl 41-MemoryIntegrations spring-boot:run
```

学习 Mem0、ReMe、Bailian 三种官方 Memory Integration，并重点识别 AgentScope Java 2.0.1 的版本边界：这些 Adapter 仍存在，但它们共同实现的 Core `LongTermMemory` 已从 2.0.0 起 `@Deprecated(forRemoval = true)`。本课只做真实 Builder/契约实验，并对比第 13 课 Harness Memory 与 application-layer memory。见
[`41-MemoryIntegrations/README.md`](41-MemoryIntegrations/README.md)。

## 42-SkillRepositoryBackends

```bash
./mvnw -pl 42-SkillRepositoryBackends spring-boot:run
```

学习 Git、MySQL、PostgreSQL、Nacos 四种官方 Skill Repository。自动化测试会创建本地临时 Git 仓库、提交 `skills/demo/SKILL.md`，再通过真实 `GitSkillRepository` clone/sync/read，数据库与 Nacos 部分则讲清 CRUD/中心化治理边界。见
[`42-SkillRepositoryBackends/README.md`](42-SkillRepositoryBackends/README.md)。

## 43-EnterpriseChannels

```bash
./mvnw -pl 43-EnterpriseChannels spring-boot:run
```

学习 DingTalk、Feishu、WeCom、GitHub、GitLab 五种官方 Channel Adapter，以及公共 `IdempotencyStore / BotLoopGuard` 的 webhook 去重与 bot-loop 防护。默认不连接真实平台。见
[`43-EnterpriseChannels/README.md`](43-EnterpriseChannels/README.md)。

## 44-EnterpriseInfrastructure

```bash
./mvnw -pl 44-EnterpriseInfrastructure spring-boot:run
```

学习 Scheduler（Quartz/XXL-Job）、Nacos（A2A/Prompt/Skill）与 Higress MCP Gateway 的企业基础设施定位；默认只构造 `ScheduleConfig` 与真实官方类型，不连接外部基础设施。见
[`44-EnterpriseInfrastructure/README.md`](44-EnterpriseInfrastructure/README.md)。

## 45-StudioAndTraining

```bash
./mvnw -pl 45-StudioAndTraining spring-boot:run
```

学习 AgentScope Studio 的消息/trace/HITL 调试链路与 `TrainingRunner` 的线上采样、reward、Trinity commit 闭环。核心测试只 build `TrainingRunner`、不 `start()`，也不初始化 Studio 网络连接。见
[`45-StudioAndTraining/README.md`](45-StudioAndTraining/README.md)。

## 46-AgentTeams

```bash
./mvnw -pl 46-AgentTeams spring-boot:run
```

AgentScope Java 2.0.1 尚未提供后续版本中的官方 AgentTeams Runtime，因此本课不升级依赖，而是在 application layer 实现 Team Board，学习 shared task board、owner/member、CAS claim、stale writer conflict 与 mailbox/member message，并明确 SubAgent 与 Agent Team 的职责边界。见
[`46-AgentTeams/README.md`](46-AgentTeams/README.md)。

## 47-AsyncToolAndWakeup

```bash
./mvnw -pl 47-AsyncToolAndWakeup spring-boot:run
```

使用 2.0.1 真实 Harness API 学习 `AsyncToolMiddleware`、`AsyncToolRegistry`、`InboxMiddleware`、`MessageBus` 与 wakeup：长耗时 Tool 超过 offload timeout 后先返回 placeholder，后台完成后把真实结果写入 registry/inbox 并唤醒对应 session。见
[`47-AsyncToolAndWakeup/README.md`](47-AsyncToolAndWakeup/README.md)。

## 48-AdvancedPermissionAndSecurity

```bash
./mvnw -pl 48-AdvancedPermissionAndSecurity spring-boot:run
```

深入 `PermissionEngine` 的 DEFAULT / ACCEPT_EDITS / EXPLORE / BYPASS / DONT_ASK 模式、ALLOW/ASK/DENY 规则顺序、dangerous path 防护与 runtime rule 更新，并验证 safety decision 在 BYPASS 下仍不能被普通兜底绕过。见
[`48-AdvancedPermissionAndSecurity/README.md`](48-AdvancedPermissionAndSecurity/README.md)。

## 49-ModelRuntimeDeepDive

```bash
./mvnw -pl 49-ModelRuntimeDeepDive spring-boot:run
```

深入 Model Runtime：学习 `GenerateOptions` 与请求级 merge、`Formatter` 的消息/响应/参数/Tool Schema 转换职责、自定义 `Model#stream()`、ThinkingBlock 与多模态 ContentBlock，并区分模型侧 parallel tool calls 与 Tool 执行侧 concurrencySafe。见
[`49-ModelRuntimeDeepDive/README.md`](49-ModelRuntimeDeepDive/README.md)。

## 50-HookAndRuntimeExtension

```bash
./mvnw -pl 50-HookAndRuntimeExtension spring-boot:run
```

统一比较 Middleware、Legacy Hook、System Hook 与 AgentEvent 的运行时扩展边界；按 2.0.1 明确旧 `Hook` 已 deprecated/forRemoval，新扩展优先使用 `MiddlewareBase`，并实验其生命周期、order 与 system hook 构造时复制语义。见
[`50-HookAndRuntimeExtension/README.md`](50-HookAndRuntimeExtension/README.md)。

## 51-ContextBudgetAndCompactionDeepDive

```bash
./mvnw -pl 51-ContextBudgetAndCompactionDeepDive spring-boot:run
```

从“会做 summary”升级到 Context Engineering：区分模型窗口、Workspace、历史 Compaction 与 Tool Result Eviction 四种预算，验证 dynamic compaction 默认公式，并用真实 `ToolResultEvictionMiddleware` 演示巨型 Tool Result 落盘 + placeholder 的 Width/Depth 治理。见
[`51-ContextBudgetAndCompactionDeepDive/README.md`](51-ContextBudgetAndCompactionDeepDive/README.md)。

## 52-StateConcurrencyAndConsistency

```bash
./mvnw -pl 52-StateConcurrencyAndConsistency spring-boot:run
```

把会话并发、State 与 DistributedStore 串成生产一致性模型：验证同一 `(userId, sessionId)` 在同 Agent 实例内串行、不同 session 可并发；再使用官方 `JdbcStore.putIfVersion()` 验证跨 JVM/Pod CAS 与 create-if-absent 幂等 claim，并明确 CAS、分布式锁和多记录事务的边界。见
[`52-StateConcurrencyAndConsistency/README.md`](52-StateConcurrencyAndConsistency/README.md)。

## 53-AgentTestingAndEvaluation

```bash
./mvnw -pl 53-AgentTestingAndEvaluation spring-boot:run
```

AgentScope Java 2.0.1 没有独立 Eval Framework，本课把评测编排放在 application layer，同时真实驱动 `ReActAgent / Toolkit / RuntimeContext / ToolResult / ChatUsage`，建立版本化 Dataset、Tool/参数/答案/Token/延迟/成本 Scorer 与可作为发布门禁的 Eval Gate。见
[`53-AgentTestingAndEvaluation/README.md`](53-AgentTestingAndEvaluation/README.md)。

## 54-AgentSecurityArchitecture

```bash
./mvnw -pl 54-AgentSecurityArchitecture spring-boot:run
```

把 Permission 放回完整纵深防御：外部 Web/PDF/RAG/Email/Tool Result 一律视为 untrusted data，结合真实 `SkillSecurityScanner`、`ToolFilter + ToolsConfig`、`PermissionEngine`、敏感路径、Sandbox、Secret Boundary、MCP trust、SSRF/Egress、Audit 与 Security Eval 建立安全架构。见
[`54-AgentSecurityArchitecture/README.md`](54-AgentSecurityArchitecture/README.md)。

## 55-ProductionAgentArchitecture

```bash
./mvnw -pl 55-ProductionAgentArchitecture spring-boot:run
```

最终综合项目把前面能力收敛为可运行 production slice：requestId 幂等 claim -> application-layer retrieval -> UNTRUSTED_DATA/security scan -> RuntimeContext -> ReActAgent -> read-only Tool -> Middleware telemetry -> ChatUsage -> durable result，并提供 deterministic Eval Gate、H2/local 与 MySQL/prod 边界、Docker/Kubernetes 部署文件以及生产检查清单。见
[`55-ProductionAgentArchitecture/README.md`](55-ProductionAgentArchitecture/README.md)。
