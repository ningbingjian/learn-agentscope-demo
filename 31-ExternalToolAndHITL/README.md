# 31-ExternalToolAndHITL

这一课学习的不是“再做一次权限确认”，而是另一种 HITL：**工具根本不在 Agent 进程里执行**。

## 1. 本课目标

学完后要能回答：

1. `Permission ASK` 和 `externalTool` 有什么区别？
2. `@Tool(externalTool = true)` 为什么不会执行 Java 方法体？
3. Agent 暂停后，什么信息必须交给外部系统？
4. 外部系统执行完成后，为什么结果必须带回同一个 `toolCallId`？
5. 为什么恢复时必须继续使用同一个 `(userId, sessionId)`？
6. `TOOL_SUSPENDED`、`RequireExternalExecutionEvent`、`ExternalExecutionResultEvent` 分别处在哪一层？

## 2. 和第 08 课的区别

第 08 课：

```text
Agent -> refund Tool
          |
          +-- Permission = ASK
                  |
                  v
              人工批准？
               /    \
             Yes    No
              |      |
              v      v
         Agent 自己执行  拒绝
```

第 31 课：

```text
Agent -> external_send_notification
          |
          +-- externalTool = true
                  |
                  v
             Agent 一定不执行
                  |
                  v
            TOOL_SUSPENDED
                  |
                  v
          外部系统 / 人工操作员
                  |
                  v
           ToolResultBlock
                  |
                  v
            同一 session 恢复
```

核心差异：

- `ASK`：是否允许 Agent 执行。
- `externalTool`：即使允许，也必须由 Agent 进程之外执行。

## 3. AgentScope Java 2.0.1 的核心契约

`@Tool` 有：

```java
@Tool(
    name = "external_send_notification",
    externalTool = true
)
```

2.0.1 源码的契约是：external tool 不运行方法体，而是让工具调用进入 suspended 状态，Agent 返回：

```text
Msg
├── GenerateReason.TOOL_SUSPENDED
└── content
    ├── ToolUseBlock
    └── suspended ToolResultBlock
```

其中最重要的是：

```text
toolCallId
```

外部结果必须用原来的 id 回来，框架才能知道：

> 这是之前那一次 pending Tool Call 的结果。

## 4. 为什么本课使用 deterministic Model

这节课故意不用真实 LLM。

原因是我们要稳定观察协议：

```text
第一次 Model 调用
    -> 一定产生 external_send_notification ToolUseBlock

外部结果回传
    -> 第二次 Model 调用
    -> 一定输出最终答案
```

这样学习重点不会被“模型这次到底调不调工具”干扰。

## 5. 第一步：定义 External Tool

看：

```text
src/main/java/.../tool/ExternalNotificationTools.java
```

核心代码：

```java
@Tool(
    name = "external_send_notification",
    description = "...",
    externalTool = true,
    readOnly = false
)
public String sendNotification(String channel, String message) {
    bodyExecutions.incrementAndGet();
    return "SHOULD_NOT_RUN_INSIDE_AGENT";
}
```

`bodyExecutions` 是故意放的探针。

正确运行时它必须永远是：

```text
0
```

否则说明外部 Tool 被 Agent 本地执行了。

## 6. 第二步：第一次调用 Agent

```java
RuntimeContext ctx = RuntimeContext.builder()
    .userId("alice")
    .sessionId("external-1")
    .build();

Msg reply = agent.call(
    new UserMessage("发送部署完成通知"),
    ctx
).block();
```

期望：

```text
reply.getGenerateReason()
    == TOOL_SUSPENDED
```

并从 reply 取：

```java
ToolUseBlock pending = reply.getFirstContentBlock(ToolUseBlock.class);
```

你需要把这些数据交给外部执行端：

```text
toolCallId
name
input
```

## 7. 第三步：外部系统真正执行

例如真正的系统可能是：

```text
Agent Service
     |
     v
Kafka / Queue
     |
     v
Operations Worker
     |
     +-- 调内部 API
     +-- 人工审批
     +-- 发邮件
     +-- 发短信
     +-- 执行线下动作
```

本课用 HTTP `/complete` 模拟它。

## 8. 第四步：构造 Tool Result

必须复用原来的：

```text
pending.id
pending.name
```

代码：

```java
ToolResultBlock result = ToolResultBlock.builder()
    .id(pending.getId())
    .name(pending.getName())
    .output(TextBlock.builder()
        .text("operator accepted notification")
        .build())
    .state(ToolResultState.SUCCESS)
    .build();
```

然后包装成 `TOOL` 消息：

```java
Msg toolMessage = Msg.builder()
    .name("external-system")
    .role(MsgRole.TOOL)
    .content(result)
    .build();
```

## 9. 第五步：恢复同一个 Session

```java
agent.call(
    List.of(toolMessage),
    RuntimeContext.builder()
        .userId("alice")
        .sessionId("external-1")
        .build()
).block();
```

注意：

```text
userId + sessionId 必须和第一次一致
```

因为 pending Tool Call 保存在这个 AgentState slot 中。

## 10. Event 层是什么

AgentScope 2.0.1 还定义：

```text
RequireExternalExecutionEvent
ExternalExecutionResultEvent
```

它们表达的是 UI / 流式协议层语义：

```text
Agent 需要外部执行
    -> RequireExternalExecutionEvent

外部执行完成
    -> ExternalExecutionResultEvent
```

而 Agent 核心的可恢复状态仍然围绕：

```text
ToolUseBlock
ToolResultBlock
ToolCallId
AgentState
TOOL_SUSPENDED
```

本模块的 `/api/external/event-contract` 可以直接查看两个 Event DTO 的类型和关联字段。

## 11. 启动

```bash
./mvnw -pl 31-ExternalToolAndHITL spring-boot:run
```

不需要 API Key。

## 12. 实验一：发起任务

```bash
curl -X POST http://localhost:18081/api/external/start \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": "alice",
    "sessionId": "external-1",
    "message": "请发送部署完成通知"
  }'
```

你会得到类似：

```json
{
  "generateReason": "TOOL_SUSPENDED",
  "suspended": true,
  "toolBodyExecutions": 0,
  "toolCallId": "external-call-1",
  "toolName": "external_send_notification",
  "toolInput": {
    "channel": "ops-demo",
    "message": "请发送部署完成通知"
  }
}
```

记住 `toolCallId`。

## 13. 实验二：外部执行完成

```bash
curl -X POST http://localhost:18081/api/external/complete \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": "alice",
    "sessionId": "external-1",
    "toolCallId": "external-call-1",
    "output": "运维系统已经成功发送通知",
    "success": true
  }'
```

结果类似：

```json
{
  "reply": "已收到外部系统执行结果：运维系统已经成功发送通知",
  "generateReason": "MODEL_STOP",
  "toolBodyExecutions": 0
}
```

## 14. 实验三：查看 Event 契约

```bash
curl http://localhost:18081/api/external/event-contract
```

## 15. 自动化测试

```bash
./mvnw -pl 31-ExternalToolAndHITL test
```

测试真正验证：

```text
第一次 call
  -> TOOL_SUSPENDED
  -> ToolUseBlock 存在
  -> external Java method body 没运行

ToolResultBlock 回填
  -> 同 session 第二次 call
  -> Agent 继续
  -> 输出外部结果
  -> method body 仍然没运行
```

## 16. 生产架构

典型架构：

```text
                  Agent Service
                       |
                       v
               external Tool Call
                       |
             TOOL_SUSPENDED
                       |
                       v
               Pending Task Store
                       |
        +--------------+--------------+
        |                             |
        v                             v
 Human Operator                  Worker Service
        |                             |
        +--------------+--------------+
                       |
                       v
                 ToolResultBlock
                       |
                       v
               Resume same session
```

生产中不要只把 pending call 放内存里。

至少保存：

```text
userId
sessionId
replyId
toolCallId
toolName
input
createdAt
status
result
```

## 17. 最容易犯的错误

### 错误 1：把 External Tool 当 Permission ASK

两者不是一回事。

### 错误 2：外部执行完重新发一条 UserMessage

错误。

你应该返回匹配原 Tool Call 的：

```text
MsgRole.TOOL + ToolResultBlock
```

### 错误 3：换了 sessionId

框架找不到原 pending call。

### 错误 4：自己生成一个新的 toolCallId

必须复用原 id。

### 错误 5：外部动作没有幂等键

生产中建议：

```text
idempotencyKey = toolCallId
```

外部系统重复消费时先查是否执行过。

## 18. 与前后课程关系

```text
08-PermissionHITL
  = 能不能执行？

31-ExternalToolAndHITL
  = 谁来执行？怎么暂停和恢复？

34-AGUIProtocol（后续）
  = 怎么把这些 HITL 事件标准化推给前端？
```
