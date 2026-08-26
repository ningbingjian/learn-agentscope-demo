# 28-MessageAndEventModel：Message 与 Event 数据模型

## 1. 为什么这一课现在才补

前面的课程已经大量使用过：

```text
Msg
UserMessage
AgentEvent
SSE
ToolUseBlock
ToolResultBlock
```

但是过去关注的是“功能怎么做”，没有把 AgentScope 最底层的数据模型完整拆开。

这一课只解决一个问题：

> 一次 Agent 执行过程中，Message 和 Event 到底分别是什么，它们如何关联？

---

## 2. 最重要的心智模型

```text
                 一次 agent.call(...)
                         │
                         ▼
                多个 AgentEvent
       ┌────────────┬────┴─────┬────────────┐
       ▼            ▼          ▼            ▼
 AgentStart     Text Delta   Tool Event   AgentEnd
       │            │          │            │
       └────────────┴────┬─────┴────────────┘
                         ▼
                   最终一条 Msg
```

一句话：

```text
Message = 完整状态
Event   = 执行过程中的增量状态
```

Message 适合：

- 会话历史；
- AgentState 持久化；
- Agent 之间传递；
- 最终业务结果。

Event 适合：

- SSE；
- 前端逐字展示；
- Tool 调用进度；
- HITL；
- 调试 Agent 内部执行过程。

---

## 3. Msg 不是一个 String

AgentScope 的 `Msg` 本质上是：

```text
Msg
├── id
├── name
├── role
├── content: List<ContentBlock>
├── metadata
├── timestamp
├── usage
└── generateReason
```

其中最重要的是：

```java
List<ContentBlock> content
```

所以一条 assistant 消息完全可能长成：

```text
Assistant Msg
├── TextBlock
├── ToolUseBlock
├── ToolResultBlock
└── TextBlock
```

而不是只有一段文本。

---

## 4. ContentBlock

当前需要重点认识：

```text
ContentBlock
├── TextBlock
├── DataBlock
├── ThinkingBlock
├── ToolUseBlock
├── ToolResultBlock
└── HintBlock
```

### TextBlock

普通文本。

### DataBlock

图片、音频、视频等多模态数据的新统一表示。

### ThinkingBlock

模型推理内容。

### ToolUseBlock

模型决定调用工具时产生：

```text
id
name
input
state
```

### ToolResultBlock

工具执行完成后的结果：

```text
id
name
output
state
metadata
```

`ToolUseBlock.id` 与 `ToolResultBlock.id` 是一次 Tool Call 的关联键。

---

## 5. Step 1：创建 Tool

本节提供：

```java
@Tool(name = "add_numbers", ...)
public long add(long left, long right) {
    return Math.addExact(left, right);
}
```

它的意义不是学习 Tool，本节只是用它制造更丰富的 Message/Event。

---

## 6. Step 2：创建 ReActAgent

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(mathTools);

return ReActAgent.builder()
        .name("message-event-agent")
        .model(model)
        .toolkit(toolkit)
        .build();
```

如果请求：

```text
计算 20 + 22
```

执行过程不只是：

```text
User -> Model -> Text
```

而更接近：

```text
UserMessage
    ↓
Model
    ↓
ToolUseBlock(add_numbers)
    ↓
Tool Result
    ↓
Model
    ↓
final assistant Msg
```

---

## 7. Step 3：手工构造一条复合 Msg

接口：

```text
GET /api/message-event/sample
```

代码先创建 Tool Call：

```java
ToolUseBlock toolUse = ToolUseBlock.builder()
        .id("call-1")
        .name("add_numbers")
        .input(Map.of("left", 20, "right", 22))
        .state(ToolCallState.PENDING)
        .build();
```

然后创建 Tool Result：

```java
ToolResultBlock toolResult = ToolResultBlock.builder()
        .id("call-1")
        .name("add_numbers")
        .output(TextBlock.builder().text("42").build())
        .state(ToolResultState.SUCCESS)
        .build();
```

最后放入同一条 assistant Msg：

```java
Msg assistant = Msg.builder()
        .name("message-event-agent")
        .role(MsgRole.ASSISTANT)
        .content(TextBlock.builder().text("准备调用工具").build())
        .content(toolUse)
        .content(toolResult)
        .content(TextBlock.builder().text("20 + 22 = 42").build())
        .build();
```

这一步的重点不是业务写法，而是观察：

```text
Msg.content
```

是有顺序的类型化 Block 列表。

---

## 8. getTextContent() 会发生什么

如果一条消息包含：

```text
TextBlock("before")
ToolUseBlock(...)
ToolResultBlock(...)
TextBlock("after")
```

调用：

```java
msg.getTextContent()
```

只提取 TextBlock 文本。

而要找 Tool Call：

```java
msg.getContentBlocks(ToolUseBlock.class)
```

找 Tool Result：

```java
msg.getContentBlocks(ToolResultBlock.class)
```

因此不要通过解析 `getTextContent()` 去猜工具状态。

---

## 9. GenerateReason

最终 assistant Msg 还可能包含退出原因：

```text
MODEL_STOP
TOOL_SUSPENDED
REASONING_STOP_REQUESTED
ACTING_STOP_REQUESTED
ALL_TOOLS_DENIED
INTERRUPTED
MAX_ITERATIONS
...
```

之前的 Interrupt、Permission HITL 等课程其实已经遇到过它。

本节把它重新放回 Message 数据模型里理解：

```text
generateReason
=
这次 Agent 为什么结束
```

---

## 10. Event 是什么

Event 是一次执行的增量输出。

典型文本生命周期：

```text
AgentStartEvent
    ↓
ModelCallStartEvent
    ↓
TextBlockStartEvent
    ↓
TextBlockDeltaEvent
    ↓
TextBlockDeltaEvent
    ↓
TextBlockEndEvent
    ↓
ModelCallEndEvent
    ↓
AgentEndEvent
```

如果还有 Tool：

```text
ToolCallStartEvent
ToolCallDeltaEvent
ToolCallEndEvent

ToolResultStartEvent
ToolResultTextDeltaEvent
ToolResultEndEvent
```

---

## 11. replyId / blockId / toolCallId

这是 Event 最重要的关联机制。

### replyId

```text
同一次 Agent 回复
```

产生的事件共享同一个 `replyId`。

### blockId

用于关联同一个内容 Block：

```text
TextBlockStart(reply-1, text-1)
TextBlockDelta(reply-1, text-1)
TextBlockEnd(reply-1, text-1)
```

### toolCallId

用于关联：

```text
Tool Call
    ↕
Tool Result
```

前端只要按照这些 ID 建状态机，就能从增量事件更新 UI。

---

## 12. Step 4：直接输出 AgentEvent SSE

本节没有把事件先转换成自己定义的 DTO，而是直接返回：

```java
Flux<AgentEvent>
```

接口：

```text
POST /api/message-event/stream
```

代码：

```java
return agent.streamEvents(
        new UserMessage(request.message()),
        context
);
```

由于 `AgentEvent` 已经有 Jackson 类型信息，所以可以直接观察不同具体事件。

---

## 13. 启动

```bash
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 28-MessageAndEventModel spring-boot:run
```

端口：

```text
18081
```

---

## 14. 实验一：观察复合 Message

```bash
curl http://localhost:18081/api/message-event/sample
```

重点看：

```text
blockTypes
```

应该能看到类似：

```text
TextBlock
ToolUseBlock
ToolResultBlock
TextBlock
```

---

## 15. 实验二：普通 call 的最终 Msg

```bash
curl -X POST http://localhost:18081/api/message-event/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"message-demo",
    "message":"请计算 20 + 22，并告诉我结果"
  }'
```

这里观察的是：

```text
一堆执行过程
      ↓
最终聚合 Msg
```

---

## 16. 实验三：观察原始事件流

```bash
curl -N -X POST http://localhost:18081/api/message-event/stream \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"event-demo",
    "message":"请计算 20 + 22，必须调用工具"
  }'
```

重点按顺序观察：

```text
AGENT_START
MODEL_CALL_START
...
TOOL_CALL_*
TOOL_RESULT_*
...
AGENT_END
```

实际事件数量取决于模型的 ReAct 行为。

---

## 17. 自动化测试

```bash
./mvnw -pl 28-MessageAndEventModel test
```

测试不调用真实模型。

第一部分验证：

```text
Msg
└── 有序 ContentBlock
```

第二部分验证：

```text
start -> delta -> end
```

并验证：

```text
replyId
blockId
```

关联关系。

---

## 18. 和第 04 课的区别

第 04 课：

```text
重点：怎么把 AgentEvent 通过 SSE 发出去
```

第 28 课：

```text
重点：AgentEvent 本身是什么
      Msg 本身是什么
      两者如何构成 AgentScope 的数据模型
```

所以不是重复课程。

---

## 19. 常见误区

### 误区 1：Msg 就是字符串

错误。

```text
Msg = metadata + role + ContentBlock[] + usage + reason ...
```

### 误区 2：SSE 里的 delta 可以直接当历史消息保存

不应该。

持久化主对象仍然应该是完整 Msg / AgentState。

### 误区 3：解析模型文本判断有没有调用 Tool

不要这样做。

直接读：

```java
ToolUseBlock
ToolResultBlock
```

### 误区 4：Event 之间没有关系

它们通过：

```text
replyId
blockId
toolCallId
```

建立关联。

---

## 20. 本课边界

这一课不深入：

- AG-UI 协议映射；
- A2A 消息转换；
- externalTool 的外部执行事件；
- 多模态模型 Provider 差异。

这些会在后续独立课程学习。

---

## 21. 一句话总结

```text
Msg 是 Agent 的完整可持久化结果，
AgentEvent 是构建这条结果过程中不断产生的增量事件。
```
