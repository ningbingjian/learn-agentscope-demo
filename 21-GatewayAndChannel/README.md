# 21-GatewayAndChannel：消息入口、会话路由与用户直连 SubAgent

## 1. 为什么不应该永远 Controller → agent.call()

前面的学习模块为了把概念讲清楚，大量使用：

```text
Spring Controller
      ↓
RuntimeContext
      ↓
agent.call()
```

这种写法没错，但当系统开始出现：

```text
多个用户
多个 session
多个 Agent
SSE
WebSocket
Slack / 飞书 / 钉钉
可暴露 SubAgent
```

应用层会逐渐承担太多路由逻辑。

AgentScope Harness 里的 Gateway / Channel 就是在解决这一层。

---

# 2. 本节目标

学完应能解释：

- Gateway 和 Channel 各自负责什么；
- `ChatUiChannel` 为什么适合 Spring HTTP/Chat UI；
- `SendOptions.userId()` 与 `SendOptions.of(userId, sessionId)` 的差异；
- `send()` 与 `sendStream()` 怎么统一走 Gateway；
- Gateway 如何保持 per-session 路由与并发边界；
- `SubagentExposedEvent` 是什么；
- 前端拿到 `subagentId` 后为什么可以绕过父 Agent 直接继续聊；
- 未来如何扩展到多个 Agent 和外部 Channel。

---

# 3. 心智模型

```text
HTTP / WebSocket / Slack / Feishu
              ↓
           Channel
              ↓
           Gateway
       ┌──────┼──────┐
       ↓      ↓      ↓
   MainAgent Agent-B Exposed SubAgent
       │
       ↓
 RuntimeContext / session
```

### Channel

负责：

```text
消息平台适配
用户/会话身份提取
把消息交给 Gateway
把回复/事件交回客户端
```

### Gateway

负责：

```text
Agent 路由
Session 路由
Per-session 调度
主 Agent / SubAgent 寻址
```

---

# 4. 项目结构

```text
21-GatewayAndChannel
├── .agentscope/workspace
│   ├── AGENTS.md
│   └── subagents/researcher.md
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/gatewayandchannel
    │   │   ├── GatewayAndChannelApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   └── web
    │   │       ├── ChannelController.java
    │   │       └── GatewayEventSseMapper.java
    │   └── resources/application.yml
    └── test
        └── java/com/example/agentscope/gatewayandchannel/ChannelRoutingTest.java
```

---

# 5. 一步步编码

## Step 1：先创建 HarnessAgent

这一步没有新东西：

```java
HarnessAgent agent = HarnessAgent.builder()
        .name("gateway-main-agent")
        .model(model)
        .workspace(Paths.get(".agentscope/workspace"))
        .build();
```

重点在下一步。

## Step 2：绑定 ChatUiChannel

```java
ChatUiChannel chat = agent.channel(ChatUiChannel.create());
```

`agent.channel(...)` 会：

```text
懒创建 internal Gateway
      ↓
注册当前 HarnessAgent 为 main agent
      ↓
channel.init(gateway)
      ↓
返回同一个 channel
```

Controller 从此不需要直接调 `agent.call()`。

## Step 3：用 SendOptions 表达业务身份

一个用户默认一个会话：

```java
SendOptions.userId("alice")
```

同一用户多个独立会话：

```java
SendOptions.of("alice", "session-a")
SendOptions.of("alice", "session-b")
```

所以：

```text
userId
  = 谁

sessionId
  = 这是谁的哪一个对话
```

这和前面 RuntimeContext 的身份模型是一致的，只是 Channel 把路由封装起来了。

## Step 4：普通请求走 chat.send

```java
Msg reply = chat.send(options, message).block();
```

内部变成：

```text
SendOptions
   ↓
MsgContext
   ↓
Gateway.run(...)
   ↓
HarnessAgent.call(..., RuntimeContext)
```

## Step 5：流式请求走 sendStream

```java
chat.sendStream(options, message)
```

返回：

```java
Flux<AgentEvent>
```

所以它与第 04 课事件体系是连续的。

本节把事件转换为 Spring SSE：

```text
AgentEvent
  ↓
GatewayEventSseMapper
  ↓
ServerSentEvent<Map<String,Object>>
```

## Step 6：暴露 SubAgent

本案例 `researcher.md`：

```yaml
---
description: 可直接暴露给用户的技术调研专家
steps: 6
mode: subagent
expose_to_user: true
---
```

核心属性是：

```yaml
expose_to_user: true
```

当主 Agent spawn researcher 后，事件流可以出现：

```text
SUBAGENT_EXPOSED
```

事件携带：

```text
subagentId
agentId
sessionId
label
```

前端最重要的是保存：

```text
subagentId
```

## Step 7：直接与 SubAgent 对话

拿到 `subagentId` 后：

```java
chat.sendToSubagent(subagentId, message)
```

或者：

```java
chat.sendToSubagentStream(subagentId, message)
```

链路变成：

```text
User
 ↓
Channel
 ↓
Gateway.runSubagent(...)
 ↓
Researcher
```

这里已经不需要先经过 Main Agent。

这就是“分支对话”的基础。

## Step 8：自动化测试验证真正的 session 路由

`ChannelRoutingTest` 使用 FakeModel，不调用真实 DashScope。

执行：

```text
alice / session-a / topic-A
alice / session-b / topic-B
```

随后读取两个 AgentState，验证：

```text
session-a 只有 topic-A
session-b 只有 topic-B
```

所以测试的不是 `SendOptions` getter，而是：

```text
ChatUiChannel
   ↓
Gateway
   ↓
RuntimeContext
   ↓
AgentState slot
```

整条链。

---

# 6. REST 接口

## 普通对话

```text
POST /api/channel/chat
```

```bash
curl -X POST http://localhost:18081/api/channel/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"chat-a",
    "message":"你好，我现在在会话 A"
  }'
```

再开另一个：

```bash
curl -X POST http://localhost:18081/api/channel/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"chat-b",
    "message":"你好，我现在在会话 B"
  }'
```

## SSE

```text
POST /api/channel/stream
```

```bash
curl -N -X POST http://localhost:18081/api/channel/stream \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"research-1",
    "message":"请派 researcher 独立研究 AgentScope Gateway，并让我可以继续直接和它聊"
  }'
```

观察：

```text
event: subagent_exposed
```

其中 data 会包含：

```json
{
  "subagentId":"...",
  "agentId":"researcher"
}
```

## 直接与 SubAgent 对话

保存上一步的 `subagentId`：

```bash
curl -X POST http://localhost:18081/api/channel/subagent/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "subagentId":"上一步得到的 id",
    "message":"继续展开 Gateway 的 per-session 并发控制"
  }'
```

流式：

```text
POST /api/channel/subagent/stream
```

---

# 7. Gateway 与 RuntimeContext 的关系

不要认为学了 Gateway 就可以忘掉 RuntimeContext。

实际上：

```text
Channel identity
      ↓
Gateway route
      ↓
MsgContext
      ↓
RuntimeContext
      ↓
HarnessAgent session slot
```

Gateway 是更上层的路由基础设施。

RuntimeContext 仍然是 Agent 执行时的身份上下文。

---

# 8. 多 Agent 路由

本节只绑定一个 main Agent。

未来有：

```text
sales
support
coder
```

可以进入：

```java
GatewayBootstrap.builder()
    .agent("sales", salesAgent)
    .agent("support", supportAgent)
    .mainAgent("sales")
    .build();
```

再通过：

```java
SendOptions.userId("alice").withAgentId("support")
```

明确路由。

这个能力建议后面和 Agent Service/部署一起继续学，而不是本节一次塞完。

---

# 9. Channel 与 HTTP Controller 的关系

Spring Controller 不是被 Channel 替代。

正确理解：

```text
HTTP Controller
= Web 传输入口

ChatUiChannel
= AgentScope 消息平台适配层

Gateway
= Agent/session 路由层
```

于是：

```text
Browser
 ↓ HTTP
Spring Controller
 ↓
ChatUiChannel
 ↓
Gateway
 ↓
Agent
```

未来换 WebSocket：

```text
WebSocket adapter
 ↓
Channel/Gateway
```

Agent 核心不需要重写。

---

# 10. 与前面课程的关系

```text
04 StreamingEvents
        ↓
21 sendStream 继续复用 AgentEvent

05 MultiUserConcurrency
        ↓
21 Gateway 按 session 做上层路由

10 RuntimeContext/StateStore
        ↓
21 SendOptions 最终映射回执行上下文

16 SubAgent
        ↓
21 SubagentExposedEvent + direct chat
```

第 21 课实际上是在把前面很多孤立能力接成“产品入口”。

---

# 11. 本节边界

本节不深入：

- Slack/飞书/钉钉具体 SDK；
- GatewayBootstrap 多主 Agent 的完整平台；
- 分布式 Gateway Registry；
- WebSocket 前端实现；
- Agent Service 独立部署协议。

下一阶段建议进入：

```text
22-AgentServiceAndDeployment
23-DistributedStateAndStorage
24-ObservabilityAndTracing
```

从这里开始，学习重点将真正转向生产架构。
