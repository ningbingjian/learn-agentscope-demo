# 04-StreamingEvents

本节学习如何使用 `streamEvents()` 获取 Agent 的实时执行事件，并通过 Spring WebFlux 将它们转换为 SSE 推送给客户端。

```text
AgentEvent -> Flux -> SSE -> 前端实时渲染
```

## 学习目标

完成本节后，你应该能够理解：

- `call()` 与 `streamEvents()` 的返回方式有什么不同。
- `Mono<Msg>` 和 `Flux<AgentEvent>` 分别表示什么。
- 为什么一次 Agent 调用会产生多个类型化事件。
- 如何将 AgentScope 事件映射为 Server-Sent Events。
- 如何实时观察文本增量、工具调用和 Agent 生命周期。

## 理论基础

### call() 为什么看不到中间过程

前三节使用：

```java
Msg reply = agent.call(message, context).block();
```

`call()` 内部会消费整个 Agent 事件流，等推理、工具调用和再推理全部完成后，只返回最终 `Msg`。

```text
请求 ------------------------------------------------> 最终 Msg
      模型推理 -> 工具调用 -> 模型再推理
```

这种方式适合只关心最终结果的后端任务，但 Web 用户必须等待所有步骤结束后才能看到内容。

### streamEvents() 返回什么

```java
Flux<AgentEvent> events = agent.streamEvents(message, context);
```

`Flux` 表示一个会在未来持续产生 `0..N` 个元素的异步序列。每当 Agent 进入新阶段，或模型产生了新的文本片段，就会向 Flux 发出一个 `AgentEvent`。

```text
请求
  │
  ├── AGENT_START
  ├── TOOL_CALL_START
  ├── TOOL_RESULT_END
  ├── TEXT_BLOCK_DELTA
  ├── TEXT_BLOCK_DELTA
  └── AGENT_END
```

`streamEvents()` 与 `call()` 执行的是同一套 ReAct 逻辑，区别只是结果的交付方式。

### 本节关心的 AgentEvent

AgentScope 还会产生模型调用、Block 开始/结束、思考、HITL 等更多事件。为了保持本节聚焦，HTTP 接口只映射下面五类：

| 事件 | 含义 | 前端用途 |
| --- | --- | --- |
| `AgentStartEvent` | 本次 Agent 调用开始 | 显示“开始处理” |
| `ToolCallStartEvent` | Agent 决定调用某个工具 | 显示工具名称和调用 ID |
| `ToolResultEndEvent` | 工具执行完成 | 显示成功、错误或拒绝状态 |
| `TextBlockDeltaEvent` | 模型新增了一段文本 | 追加到当前消息 |
| `AgentEndEvent` | 本次 Agent 调用结束 | 停止 loading 状态 |

### SSE 是什么

Server-Sent Events 是基于 HTTP 的服务端单向推送协议。响应类型为：

```text
Content-Type: text/event-stream
```

每条事件由若干文本字段组成：

```text
id: <event-id>
event: tool_call_start
data: {"type":"TOOL_CALL_START","toolName":"calculate"}
```

| SSE 字段 | 作用 |
| --- | --- |
| `id` | 事件唯一标识 |
| `event` | 前端订阅的事件名称 |
| `data` | 实际 JSON 数据 |

SSE 是服务端到客户端的单向流，不等于 WebSocket。用户输入仍然通过普通 HTTP 请求发送。

### 为什么使用 Spring WebFlux

WebFlux 能直接把 `Flux<ServerSentEvent<?>>` 写入 HTTP 响应：

```text
AgentScope Flux<AgentEvent>
            ↓ map
Flux<ServerSentEvent<Map<String, Object>>>
            ↓
HTTP text/event-stream
```

Controller 不使用 `.block()`，每个事件到达后就可以立即写向网络。

## 项目结构

```text
04-StreamingEvents
├── pom.xml
└── src
    ├── main
    │   ├── java/com/example/agentscope/streamingevents
    │   │   ├── StreamingEventsApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   ├── tool/CalculatorTools.java
    │   │   └── web
    │   │       ├── AgentEventSseMapper.java
    │   │       └── StreamingChatController.java
    │   └── resources/application.yml
    └── test/java/com/example/agentscope/streamingevents/web
        └── AgentEventSseMapperTest.java
```

## 核心代码

### 1. 获取 AgentEvent 流

```java
return agent.streamEvents(request.message(), RuntimeContext.empty())
        .filter(AgentEventSseMapper::supports)
        .map(AgentEventSseMapper::toSse);
```

这里使用两个 Reactor 算子：

- `filter` 只保留本节关心的五类事件。
- `map` 把每个 `AgentEvent` 转换为 Spring `ServerSentEvent`。

此时方法只是建立了流处理管道。WebFlux 订阅 Flux 以后，真正的 Agent 调用才开始执行。

### 2. 根据具体事件填充数据

```java
if (event instanceof TextBlockDeltaEvent delta) {
    data.put("delta", delta.getDelta());
} else if (event instanceof ToolCallStartEvent start) {
    data.put("toolCallId", start.getToolCallId());
    data.put("toolName", start.getToolCallName());
} else if (event instanceof ToolResultEndEvent end) {
    data.put("toolName", end.getToolCallName());
    data.put("state", end.getState().name());
}
```

不同事件拥有不同字段，因此映射器先写入统一的 `type`，再根据具体事件类型添加专属数据。

### 3. 生成 SSE

```java
return ServerSentEvent.<Map<String, Object>>builder(data)
        .id(event.getId())
        .event(event.getType().name().toLowerCase(Locale.ROOT))
        .build();
```

Spring WebFlux 会完成 JSON 序列化和 SSE 文本编码。

## 启动

在项目根目录执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 04-StreamingEvents spring-boot:run
```

服务端口是 `18081`。

## 测试 SSE

`curl` 的 `-N` 参数用于关闭客户端输出缓冲，便于观察事件逐条到达：

```bash
curl -N -X POST http://localhost:18081/api/chat/stream \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"message":"请使用计算工具计算 123.45 乘以 67.89"}'
```

可以观察到类似顺序：

```text
event:agent_start
data:{"type":"AGENT_START",...}

event:tool_call_start
data:{"type":"TOOL_CALL_START","toolName":"calculate",...}

event:tool_result_end
data:{"type":"TOOL_RESULT_END","toolName":"calculate","state":"SUCCESS",...}

event:text_block_delta
data:{"type":"TEXT_BLOCK_DELTA","delta":"123.45"...}

event:agent_end
data:{"type":"AGENT_END",...}
```

`TextBlockDeltaEvent` 通常会出现多次，客户端应将相同 `replyId + blockId` 的 `delta` 按到达顺序追加，而不是用后一段覆盖前一段。

## 浏览器调用注意

浏览器原生 `EventSource` 只支持 GET，本节接口为了保留 JSON 请求体而使用 POST。前端可使用 `fetch()` 读取 `ReadableStream`，或者在实际项目中拆成“POST 创建任务 + GET 订阅事件”两个接口。

## 本节边界

本节只学习 `AgentEvent + Flux + SSE`。计算工具仅用来产生可观察的工具事件，不再重复讲解 Tool Calling。本节不引入会话持久化、Workspace、HITL、MCP、断线重连或事件回放。

## 延伸阅读

- [AgentScope Java：消息与事件](https://java.agentscope.io/v2/zh/docs/building-blocks/message-and-event.html)
- [AgentScope Java：Agent 接口](https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html)
