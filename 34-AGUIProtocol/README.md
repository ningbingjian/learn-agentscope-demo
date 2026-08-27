# 34-AGUIProtocol

本节学习 AgentScope Java 2.0.1 的 **AG-UI Protocol**。

前面的课程已经能把 `AgentEvent` 通过自己的 SSE Controller 发给浏览器，但那仍然是“应用自己定义协议”。AG-UI 解决的是另一层问题：

> 如何把 Agent 的运行过程按照一个标准的 UI 事件协议输出，让兼容 AG-UI 的前端不需要理解 AgentScope 内部事件类型？

本模块不使用真实大模型，使用 `AguiDemoModel` 产生确定性结果，因此不需要 `DASHSCOPE_API_KEY`。

---

## 1. 这一课解决什么问题

第 28 课已经知道 AgentScope 内部有：

```text
AgentStartEvent
TextBlockStartEvent
TextBlockDeltaEvent
TextBlockEndEvent
ToolCallStartEvent
ToolResultEndEvent
AgentEndEvent
```

如果前端直接消费这些事件，它就和 AgentScope Java 的内部模型强绑定：

```text
Browser
   ↓
理解 AgentScope Java AgentEvent
   ↓
自己维护文本 / thinking / tool / interrupt UI 状态
```

AG-UI 在中间增加一个协议适配层：

```text
Browser / Copilot UI / 自研 Chat UI
                ↑
             AG-UI
                ↑
        AguiAgentAdapter
                ↑
         AgentEvent stream
                ↑
           ReActAgent
```

于是前端主要处理：

```text
RUN_STARTED
TEXT_MESSAGE_START
TEXT_MESSAGE_CONTENT
TEXT_MESSAGE_END
TOOL_CALL_START
TOOL_CALL_ARGS
TOOL_CALL_END
TOOL_CALL_RESULT
RUN_FINISHED
```

而不用认识 AgentScope 内部 Java 类。

---

## 2. 和第 04 / 28 课有什么区别

### 04-StreamingEvents

学习的是：

```text
Agent Event
   ↓
我们自己的 Spring SSE
```

重点是“怎么流式输出”。

### 28-MessageAndEventModel

学习的是：

```text
Msg
ContentBlock
AgentEvent
replyId
blockId
```

重点是 AgentScope 内部数据模型。

### 34-AGUIProtocol

学习的是：

```text
AgentScope AgentEvent
        ↓
AguiAgentAdapter
        ↓
标准 AG-UI Event
        ↓
Frontend
```

重点是“协议适配”。

---

## 3. 本模块结构

```text
34-AGUIProtocol/
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/aguiprotocol
    │   │   ├── AguiProtocolApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   ├── model/AguiDemoModel.java
    │   │   └── web/AguiInfoController.java
    │   └── resources/application.yml
    └── test
        └── java/com/example/agentscope/aguiprotocol/AguiProtocolContractTest.java
```

---

## 4. 第一步：引入正确的 2.0.1 依赖

Spring Boot 项目需要：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-core</artifactId>
</dependency>

<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-agui</artifactId>
</dependency>

<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-agui-spring-boot-starter</artifactId>
</dependency>
```

这里特意显式加入 `agentscope-extensions-agui`。

原因是 2.0.1 的 AG-UI Starter POM 把 AG-UI Core 声明成 `provided + optional`，而本课程代码会直接使用：

```java
AguiAgentAdapter
RunAgentInput
AguiAgentRegistry
```

所以不要依赖传递依赖的偶然行为。

---

## 5. 第二步：准备一个 deterministic Model

本课不是研究模型能力，因此 `AguiDemoModel` 只做：

```text
最后一条输入
    ↓
"AG-UI demo reply: " + input
```

核心代码：

```java
public Flux<ChatResponse> stream(
        List<Msg> messages,
        List<ToolSchema> tools,
        GenerateOptions options) {
    String input = messages.get(messages.size() - 1).getTextContent();
    return Flux.just(ChatResponse.builder()
            .content(List.of(TextBlock.builder()
                    .text("AG-UI demo reply: " + input)
                    .build()))
            .finishReason("stop")
            .build());
}
```

这样自动化测试只验证：

```text
AgentEvent → AG-UI Event
```

不会受网络、模型输出随机性影响。

---

## 6. 第三步：注册 Agent 与 AguiAgentRegistry

AG-UI Spring Starter 有一个很重要的激活条件：

```text
Spring Context 中必须存在 AguiAgentRegistry Bean
```

因此本课主动注册：

```java
@Bean
AguiAgentRegistry aguiAgentRegistry(ReActAgent aguiAgent) {
    AguiAgentRegistry registry = new AguiAgentRegistry();
    registry.register("assistant", aguiAgent);
    registry.register("default", aguiAgent);
    return registry;
}
```

`AguiAgentRegistry` 支持两种模式：

```text
register(id, agent)
        ↓
singleton agent

registerFactory(id, supplier)
        ↓
每次获取创建新 Agent
```

本课先使用 singleton，因为 ReActAgent 2.0.1 已经能通过 `RuntimeContext` 的 session 隔离不同调用。

---

## 7. 第四步：理解 RunAgentInput

AG-UI 请求不是简单的：

```json
{"message":"hello"}
```

而是一个完整的 `RunAgentInput`：

```text
RunAgentInput
├── threadId
├── runId
├── messages
├── tools
├── context
├── state
├── forwardedProps
└── resume
```

本课最重要的是两个 ID：

### threadId

表示对话线程。

AgentScope 的 `AguiAgentAdapter` 会做：

```text
RunAgentInput.threadId
          ↓
RuntimeContext.sessionId
```

所以：

```text
thread-1 → session thread-1
thread-2 → session thread-2
```

### runId

表示一次执行。

同一个 thread 可以有多次 run：

```text
thread-1
├── run-1
├── run-2
└── run-3
```

这两个概念不要混淆。

---

## 8. 第五步：核心 AguiAgentAdapter

真正的协议桥是：

```java
AguiAgentAdapter adapter = new AguiAgentAdapter(
        agent,
        AguiAdapterConfig.builder()
                .enableReasoning(true)
                .build());

Flux<AguiEvent> events = adapter.run(input);
```

内部流程：

```text
RunAgentInput
     ↓
AguiMessageConverter
     ↓
List<Msg>
     ↓
ReActAgent.streamEvents(...)
     ↓
Flux<AgentEvent>
     ↓
AgentEventConverterRegistry
     ↓
Flux<AguiEvent>
```

---

## 9. 事件映射

常见映射：

| AgentScope | AG-UI |
|---|---|
| `AgentStartEvent` | `RUN_STARTED` |
| 文本 start | `TEXT_MESSAGE_START` |
| 文本 delta | `TEXT_MESSAGE_CONTENT` |
| 文本 end | `TEXT_MESSAGE_END` |
| Thinking | `REASONING_MESSAGE_*` |
| Tool call | `TOOL_CALL_*` |
| Tool result | `TOOL_CALL_RESULT` |
| `AgentEndEvent` | `RUN_FINISHED` |
| 未识别事件 | `RAW` |

前端可以用一个状态机消费：

```text
RUN_STARTED
    ↓
TEXT_MESSAGE_START
    ↓
TEXT_MESSAGE_CONTENT × N
    ↓
TEXT_MESSAGE_END
    ↓
RUN_FINISHED
```

---

## 10. ToolMergeMode

AG-UI 前端还可以在请求中提交 Tool Schema。

这些 Tool 只在当前 run 临时注入 Agent Toolkit。

2.0.1 有三种合并策略：

```text
FRONTEND_ONLY
AGENT_ONLY
MERGE_FRONTEND_PRIORITY
```

默认：

```text
MERGE_FRONTEND_PRIORITY
```

意思是：

```text
Agent 自己的 Tools
        +
Frontend Tools
        ↓
临时 Tool Surface
```

同名时前端版本优先。

run 结束后临时 Tool 会被清理，不会永久污染 Agent。

---

## 11. HITL 在 AG-UI 中怎么表达

前面 08 / 31 课已经学过：

```text
Permission ASK
External Tool
```

在 AG-UI 层不需要前端认识：

```text
RequireUserConfirmEvent
TOOL_SUSPENDED
```

而会转换成类似：

```text
RUN_FINISHED
└── outcome.type = interrupt
    └── interrupts[]
        ├── toolCallId
        ├── message
        └── metadata
```

用户处理完成以后，下一次 `RunAgentInput` 带：

```text
resume[]
```

Adapter 再桥接回 AgentScope Core 所需的结果。

因此协议层职责是：

```text
Core HITL 状态
      ↕
AG-UI interrupt / resume
      ↕
前端审批 UI
```

---

## 12. Spring Boot 自动入口

`application.yml`：

```yaml
agentscope:
  agui:
    path-prefix: /agui
    default-agent-id: assistant
    enable-path-routing: true
    enable-reasoning: true
```

Starter 自动注册：

```text
POST /agui/run
POST /agui/run/{agentId}
```

返回：

```text
text/event-stream
```

Agent 选择优先级大致是：

```text
path agentId
    ↓
X-Agent-Id header
    ↓
forwardedProps.agentId
    ↓
default-agent-id
    ↓
"default"
```

---

## 13. 启动

```bash
./mvnw -pl 34-AGUIProtocol spring-boot:run
```

先检查：

```bash
curl http://localhost:18081/api/agui/info
```

示例返回：

```json
{
  "protocol": "AG-UI",
  "registeredAgents": 2,
  "assistantRegistered": true,
  "sessionKey": "RunAgentInput.threadId"
}
```

---

## 14. 调用 AG-UI

```bash
curl -N -X POST http://localhost:18081/agui/run/assistant \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "threadId":"thread-1",
    "runId":"run-1",
    "messages":[
      {
        "id":"msg-1",
        "role":"user",
        "content":"你好 AG-UI"
      }
    ],
    "tools":[],
    "context":[],
    "state":{},
    "forwardedProps":{}
  }'
```

重点观察事件：

```text
RUN_STARTED
TEXT_MESSAGE_START
TEXT_MESSAGE_CONTENT
TEXT_MESSAGE_END
RUN_FINISHED
```

---

## 15. 自动化测试

```bash
./mvnw -pl 34-AGUIProtocol test
```

测试做两件事：

1. 验证 Spring Starter 因 `AguiAgentRegistry` Bean 而真正创建 `AguiMvcController / AguiRestController`。
2. 直接调用 `AguiAgentAdapter`，验证 AgentScope Event 被转换成 AG-UI 生命周期事件。

不访问任何外部网络。

---

## 16. 本课最终模型

```text
                 Frontend
                    │
              RunAgentInput
                    │
                    ▼
             AG-UI Endpoint
                    │
                    ▼
          AguiAgentRegistry
                    │
                    ▼
          AguiAgentAdapter
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
  RuntimeContext          Tool injection
  session=threadId        run scoped
        │
        ▼
    ReActAgent
        │
        ▼
  Flux<AgentEvent>
        │
        ▼
  Flux<AguiEvent>
        │
        ▼
       SSE UI
```

下一课进入 A2A：让 Agent 不只是服务前端，而是按照标准协议被其他 Agent 调用。
