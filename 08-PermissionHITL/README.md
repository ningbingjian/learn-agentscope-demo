# 08-PermissionHITL

本节学习 AgentScope Java 2.x 的 **Permission System + Human In The Loop（HITL）**：当 Agent 想执行一个有副作用的 Tool 时，先让权限系统决定 `ALLOW / DENY / ASK`；如果结果是 `ASK`，Agent 暂停，等待用户确认后再恢复执行。

本案例使用一个**模拟退款工具**，不会调用真实支付系统。

## 学习目标

完成本节后，你应该能够理解：

- 为什么“Agent 会调用工具”还不够，生产系统还需要权限控制。
- `PermissionBehavior.ALLOW / DENY / ASK` 分别表示什么。
- `PermissionContextState`、`PermissionRule`、`PermissionMode` 的职责。
- 为什么 `ASK` 会让 Agent 返回 `GenerateReason.PERMISSION_ASKING`。
- 如何从返回的 `Msg` 中找到 `ToolCallState.ASKING` 的 `ToolUseBlock`。
- `ConfirmResult` 如何把用户批准/拒绝结果交回 Agent。
- 为什么恢复执行必须继续使用相同的 `(userId, sessionId)`。
- HITL 和第 06 课 `interrupt` 的区别。

## 先理解真实问题

第 03 课的 Tool Calling 大致是：

```text
用户请求
   ↓
Agent 决定调用 Tool
   ↓
Tool 直接执行
   ↓
结果返回 Agent
```

对于计算器没有问题。

但下面这些工具不能默认直接执行：

```text
退款
删除数据
发送邮件
执行 Shell
修改生产配置
发布内容
```

更合理的流程应该是：

```text
Agent 想执行 Tool
        ↓
Permission System
   ┌────┼────┐
 ALLOW DENY  ASK
   │     │     │
执行   拒绝   暂停
               ↓
           用户确认
          ┌────┴────┐
        approve    deny
          │          │
        执行       不执行
```

## AgentScope Permission System 的三种结果

### ALLOW

```text
权限检查通过
    ↓
Tool 直接执行
```

适合明确安全的操作。

### DENY

```text
权限检查拒绝
    ↓
Tool 不执行
    ↓
拒绝结果回到 Agent
```

适合明确禁止的操作。

### ASK

```text
权限检查需要人工确认
        ↓
Tool 暂时不执行
        ↓
Agent 返回 PERMISSION_ASKING
        ↓
应用展示待确认操作
        ↓
用户批准 / 拒绝
        ↓
第二次 agent.call(...)
        ↓
Agent 恢复
```

本节只重点学习 `ASK`。

## 本案例为什么选“退款”

我们定义一个工具：

```java
@Tool(name = "issue_refund", readOnly = false)
public String issueRefund(String orderId, double amount) {
    return "SIMULATED_REFUND_OK ...";
}
```

它具有明显的副作用语义，所以非常适合解释 HITL。

但本案例不会真的退款，只返回模拟结果。

## 配置 ASK 规则

核心配置：

```java
PermissionRule askBeforeRefund = new PermissionRule(
        "issue_refund",
        null,
        PermissionBehavior.ASK,
        "lesson-policy"
);

PermissionContextState permissionContext =
        PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT)
                .addAskRule("issue_refund", askBeforeRefund)
                .build();
```

其中：

```text
issue_refund
    ↓
命中 ASK rule
    ↓
不能立即执行
```

`ruleContent = null` 表示这条规则匹配该 Tool 的所有调用。

## 把权限上下文交给 Agent

```java
return ReActAgent.builder()
        .name("refund-agent")
        .model(model)
        .toolkit(toolkit)
        .permissionContext(refundPermissionContext)
        .build();
```

这一步之后，Permission Engine 会参与 Tool 执行流程。

## 第一次调用：Agent 暂停

用户请求：

```text
请为订单 O-1001 退款 299 元
```

Agent 根据系统提示调用：

```text
issue_refund(orderId="O-1001", amount=299)
```

但权限规则是 ASK，因此：

```text
Agent
  ↓
issue_refund
  ↓
PermissionEngine
  ↓
ASK
  ↓
Tool 不执行
  ↓
Msg.generateReason = PERMISSION_ASKING
```

返回的 Msg 中还包含：

```text
ToolUseBlock
├── id
├── name = issue_refund
├── input
└── state = ASKING
```

## 第二次调用：把用户决定交回来

用户批准后，我们并不是重新说一次：

```text
“好，我批准”
```

而是构建 AgentScope 能识别的 `ConfirmResult`：

```java
List<ConfirmResult> confirmResults =
        askingTools.stream()
                .map(tool -> new ConfirmResult(true, tool))
                .toList();
```

然后放进消息 metadata：

```java
Map<String, Object> metadata = new HashMap<>();
metadata.put(Msg.METADATA_CONFIRM_RESULTS, confirmResults);

Msg resumeMessage = Msg.builder()
        .name("user")
        .role(MsgRole.USER)
        .textContent("approved")
        .metadata(metadata)
        .build();
```

再调用同一个 session：

```java
agent.call(
        List.of(resumeMessage),
        RuntimeContext.builder()
                .userId("alice")
                .sessionId("refund-001")
                .build()
).block();
```

AgentScope 会识别 confirmation metadata，继续之前暂停的流程。

## 为什么必须使用同一个 userId + sessionId

HITL 本质上是：

```text
第一次 call
    ↓
会话状态停在“等待确认”
    ↓
第二次 call
    ↓
恢复同一个状态
```

所以：

```text
alice/refund-001  ASK
        ↓
alice/refund-001  confirm   ✅
```

如果改成：

```text
alice/refund-002  confirm   ❌
```

就是另外一个会话，没有之前的待确认工具调用。

## 项目结构

```text
08-PermissionHITL
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/permissionhitl
    │   │   ├── PermissionHitlApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   ├── support/HitlSupport.java
    │   │   ├── tool/RefundTools.java
    │   │   └── web/RefundController.java
    │   └── resources/application.yml
    └── test/java/com/example/agentscope/permissionhitl
        └── HitlSupportTest.java
```

## 一步步实现

### 第一步：写一个有副作用语义的 Tool

```java
@Component
public class RefundTools {

    @Tool(name = "issue_refund", readOnly = false)
    public String issueRefund(String orderId, double amount) {
        return "SIMULATED_REFUND_OK ...";
    }
}
```

这里继续采用上一课讨论过的方式：自己的简单工具类直接使用 `@Component`。

### 第二步：为 Tool 配 ASK Rule

```java
PermissionRule rule = new PermissionRule(
        "issue_refund",
        null,
        PermissionBehavior.ASK,
        "lesson-policy"
);
```

### 第三步：构建 PermissionContextState

```java
PermissionContextState.builder()
        .mode(PermissionMode.DEFAULT)
        .addAskRule("issue_refund", rule)
        .build();
```

### 第四步：交给 ReActAgent

```java
ReActAgent.builder()
        .toolkit(toolkit)
        .permissionContext(permissionContext)
        .build();
```

### 第五步：第一次 HTTP 请求启动退款

```text
POST /api/refunds/start
```

Controller 正常调用 Agent。

当 Agent 返回：

```java
reply.getGenerateReason() == GenerateReason.PERMISSION_ASKING
```

就说明当前会话停在 HITL 等待状态。

### 第六步：保存/找到 ASKING ToolUseBlock

本案例没有额外设计一套 Pending 表，而是直接从当前 session 的 `AgentState.context` 中寻找最近的 ASKING Tool：

```java
agent.getAgentState(userId, sessionId).getContext()
```

这能更直接地观察 AgentScope 自己维护的会话状态。

### 第七步：用户批准或拒绝

```text
POST /api/refunds/confirm
```

请求体里的：

```json
{
  "approved": true
}
```

会被转换成 `ConfirmResult`。

## 启动

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 08-PermissionHITL spring-boot:run
```

## 实验一：启动退款，观察 ASK

```bash
curl -X POST http://localhost:18081/api/refunds/start \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"refund-001",
    "orderId":"O-1001",
    "amount":299
  }'
```

预期第一次不会执行 Tool，而是类似：

```json
{
  "userId": "alice",
  "sessionId": "refund-001",
  "status": "WAITING_CONFIRMATION",
  "generateReason": "PERMISSION_ASKING",
  "pendingTools": [
    {
      "id": "...",
      "name": "issue_refund",
      "input": {
        "orderId": "O-1001",
        "amount": 299
      }
    }
  ]
}
```

## 实验二：批准

必须使用相同 `userId + sessionId`：

```bash
curl -X POST http://localhost:18081/api/refunds/confirm \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"refund-001",
    "approved":true
  }'
```

此时 `issue_refund` 才允许真正执行。

因为这是教学 Tool，所谓“执行退款”只会返回：

```text
SIMULATED_REFUND_OK ...
```

## 实验三：拒绝

换一个 session 重新开始：

```bash
curl -X POST http://localhost:18081/api/refunds/start \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"refund-002",
    "orderId":"O-2002",
    "amount":99
  }'
```

然后：

```bash
curl -X POST http://localhost:18081/api/refunds/confirm \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"refund-002",
    "approved":false
  }'
```

Tool 不应该执行成功。

## 自动化测试

测试不访问 DashScope，而是人工构造：

```text
ToolUseBlock(state = ASKING)
       ↓
HitlSupport.findLatestAskingTools
       ↓
HitlSupport.buildResumeMessage(true, ...)
       ↓
Msg.METADATA_CONFIRM_RESULTS
```

运行：

```bash
./mvnw -pl 08-PermissionHITL test
```

## Permission Mode 先建立整体认识

本节只用 `DEFAULT`，但你应该先知道 AgentScope 还有这些模式：

| Mode | 简化理解 |
| --- | --- |
| `DEFAULT` | 没有明确规则时偏向询问 |
| `ACCEPT_EDITS` | 适合用户在场的编辑场景 |
| `EXPLORE` | 偏只读探索 |
| `BYPASS` | 高信任沙箱下尽量放行 |
| `DONT_ASK` | 无法人工确认时把 ASK 变成拒绝 |

生产系统不能简单理解为“BYPASS = 什么都能做”，Tool 自身的安全检查和 deny/ask 规则仍然很重要。

## HITL 和 Interrupt 的区别

第 06 课：

```text
interrupt
= 用户要求停止当前执行
```

第 08 课：

```text
HITL / ASK
= Agent 主动暂停，等待用户决定能不能继续某个动作
```

方向完全不同：

```text
06：用户 → 停 Agent
08：Agent → 问用户
```

## 本节边界

本节只学习：

```text
Tool Call
   ↓
Permission ASK
   ↓
PERMISSION_ASKING
   ↓
ConfirmResult
   ↓
恢复执行
```

暂不展开：

- suggested rules 长期记忆
- 危险路径保护
- 自定义 ToolBase#checkPermissions
- 权限规则持久化
- 多个 Tool 同时等待确认的复杂 UI

下一节 `09-MiddlewareLifecycle` 会从另一个角度观察 Agent：**如何在 Agent、Reasoning、Model Call、Acting 等生命周期阶段统一插入日志、指标和审计逻辑。**

## 延伸阅读

- AgentScope Java：Permission System
  https://java.agentscope.io/v2/zh/docs/building-blocks/permission-system.html
