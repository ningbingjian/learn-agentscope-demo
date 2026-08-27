# 35-A2AProtocol

本节学习 AgentScope Java 2.0.1 的 **A2A（Agent-to-Agent）协议**。

第 34 课解决的是：

```text
Agent ↔ Frontend
```

这一课解决的是：

```text
Agent ↔ Agent
```

更准确地说，是让不同框架、不同服务、甚至不同团队维护的 Agent，通过标准 A2A 协议互相发现与调用。

本模块使用本地 deterministic `A2aDemoModel`，核心实验不需要任何模型 API Key。

---

## 1. 先区分三个协议

到目前为止项目已经有三套不同的外部交互方式。

### Agent Protocol（第 22 课）

```text
Business System
      ↓ HTTP task
Agent Service
```

重点是：

```text
任务提交
任务状态
SSE events
cancel / resume
remote subagent task
```

### AG-UI（第 34 课）

```text
Frontend
   ↓ UI event protocol
Agent
```

重点是前端实时渲染。

### A2A（本课）

```text
Agent A
   ↓ A2A
Agent B
```

重点是 Agent 平台之间的标准互操作。

---

## 2. AgentScope 2.0.1 的 A2A 组成

官方 A2A 扩展被拆成两个独立模块：

```text
agentscope-extensions-a2a-client
agentscope-extensions-a2a-server
```

### Client

把远端 A2A Agent 包装成本地：

```java
A2aAgent remote = A2aAgent.builder()
        .name("remote-agent")
        .agentCard(card)
        .build();
```

然后调用方仍然面对统一的：

```java
Agent
```

因此远程 Agent 可以继续参与：

```text
Pipeline
SubAgent
业务编排
普通 agent.call(...)
```

### Server

把本地 ReActAgent 暴露为 A2A Server。

底层核心类：

```text
AgentScopeA2aServer
```

它本身不负责监听端口，而是组装：

```text
AgentRunner
AgentCard
Transport
TaskStore
QueueManager
Request Handler
```

Web 监听由 Spring Boot 等框架负责。

---

## 3. 2.0.1 的 Starter 名称纠正

本课按 **v2.0.1 源码中的真实 Maven artifact** 使用：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-a2a-spring-boot-starter</artifactId>
</dependency>
```

不要混用旧资料里出现的：

```text
agentscope-spring-boot-starter-a2a-server
```

学习项目锁定的是 2.0.1，因此代码与锁定 tag 的实际 POM 保持一致。

---

## 4. 本模块结构

```text
35-A2AProtocol/
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/a2aprotocol
    │   │   ├── A2aProtocolApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   ├── model/A2aDemoModel.java
    │   │   └── web/A2aInfoController.java
    │   └── resources/application.yml
    └── test
        └── java/com/example/agentscope/a2aprotocol/A2aProtocolContractTest.java
```

---

## 5. 第一步：提供 ReActAgent Bean

Starter 的自动配置会寻找：

```text
ReActAgent Bean
        或
ReActAgent.Builder Bean
```

本课直接提供：

```java
@Bean
ReActAgent a2aAgent(Model model) {
    return ReActAgent.builder()
            .name("lesson-a2a-agent")
            .model(model)
            .build();
}
```

Starter 看到这个 Bean 后会创建：

```text
ReActAgent
   ↓
AgentRunner
   ↓
AgentScopeA2aServer
   ↓
├── AgentCardController
├── A2aJsonRpcController
└── ServerReadyListener
```

---

## 6. AgentCard 是什么

A2A 不是拿到一个 URL 就直接乱调。

远端 Agent 先通过 AgentCard 描述自己：

```text
AgentCard
├── name
├── description
├── url
├── version
├── input modes
├── output modes
├── skills
├── security
└── preferred transport
```

它很像：

```text
Agent 的服务发现元数据 + 能力说明书
```

本课配置：

```yaml
agentscope:
  a2a:
    server:
      card:
        name: lesson-a2a-agent
        description: AgentScope Java 2.0.1 A2A lesson agent
        url: http://localhost:18081
        version: 1.0.0
        default-input-modes:
          - text
        default-output-modes:
          - text
        preferred-transport: JSONRPC
```

---

## 7. well-known AgentCard

Starter 自动开放：

```text
GET /.well-known/agent-card.json
```

启动：

```bash
./mvnw -pl 35-A2AProtocol spring-boot:run
```

查询：

```bash
curl http://localhost:18081/.well-known/agent-card.json
```

你应该能看到类似：

```json
{
  "name": "lesson-a2a-agent",
  "description": "AgentScope Java 2.0.1 A2A lesson agent",
  "url": "http://localhost:18081",
  "version": "1.0.0"
}
```

这就是远程 A2A Client 自动发现 Agent 的入口。

---

## 8. JSON-RPC Transport

2.0.1 Starter 默认支持 JSON-RPC Transport。

配置：

```yaml
agentscope:
  a2a:
    server:
      transports:
        jsonrpc:
          enabled: true
```

Spring Controller 的核心入口位于：

```text
POST /
```

支持：

```text
application/json
text/event-stream
```

内部：

```text
HTTP JSON-RPC Request
        ↓
A2aJsonRpcController
        ↓
JsonRpcTransportWrapper
        ↓
AgentScopeA2aServer
        ↓
AgentRunner
        ↓
ReActAgent
```

如果结果是流，它会转换成 SSE JSON-RPC response。

---

## 9. A2A Client

已知 AgentCard 时：

```java
A2aAgent remote = A2aAgent.builder()
        .name("remote-translator")
        .agentCard(card)
        .build();
```

不知道 Card 时，可以走 well-known：

```java
WellKnownAgentCardResolver resolver =
        new WellKnownAgentCardResolver(
                "http://other-service:8080",
                "/.well-known/agent-card.json",
                Map.of());

A2aAgent remote = A2aAgent.builder()
        .name("remote")
        .agentCardResolver(resolver)
        .build();
```

然后：

```java
remote.call(new UserMessage("Translate: 你好"))
```

调用方看起来还是普通 Agent。

---

## 10. 为什么 A2aAgent 很重要

如果没有统一 Agent 抽象：

```text
Local Agent → agent.call()
Remote Agent → httpClient.post(...)
Other Agent Platform → anotherSdk.invoke(...)
```

业务编排层会到处判断。

A2A Client 把它统一成：

```text
Local ReActAgent ─────┐
Remote A2aAgent ──────┼→ Agent abstraction
Other wrapped Agent ──┘
```

于是上层可以继续：

```text
Orchestrator
   ├── LocalAgent
   ├── A2aAgent
   └── A2aAgent
```

---

## 11. A2A 与 SubAgent 的关系

第 16 课 SubAgent 更多强调：

```text
一个 Harness 内部的任务委派
```

A2A 强调：

```text
跨服务 / 跨框架 / 跨平台 Agent 调用
```

两者可以组合：

```text
Harness Main Agent
        ↓
Remote SubAgent abstraction
        ↓
A2A Client
        ↓ network
External Agent Platform
```

---

## 12. A2A 与 Agent Protocol 的关系

不要认为两个都是 HTTP 就等价。

```text
Agent Protocol
= task-service semantics

A2A
= agent interoperability semantics
```

Agent Protocol 更像：

```text
提交任务 → taskId → wait/events/cancel/resume
```

A2A 更关注：

```text
AgentCard
message/task protocol
transport
remote Agent abstraction
```

---

## 13. 本课 HTTP 辅助接口

为了方便观察，我们额外提供：

```text
GET /api/a2a/info
```

它直接读取 `AgentScopeA2aServer.getAgentCard()`，返回：

```text
name
url
version
well-known path
JSON-RPC path
```

这不是 A2A 标准协议的一部分，只是教学观察接口。

---

## 14. 自动化测试

```bash
./mvnw -pl 35-A2AProtocol test
```

测试验证：

```text
Spring Boot Context
        ↓
AgentScopeA2aServer      ✅
AgentCardController      ✅
A2aJsonRpcController     ✅
```

并且用 MockMvc 真请求：

```text
GET /.well-known/agent-card.json
```

验证 `name / url`。

第二个测试用同一张 AgentCard：

```java
A2aAgent.builder()
        .name("remote-wrapper")
        .agentCard(card)
        .build();
```

只验证 Client 包装契约，不真正发送网络请求。

---

## 15. 生产时还要考虑什么

教学环境使用默认内存组件。

生产还要考虑：

```text
TaskStore
QueueManager
PushNotificationConfigStore
PushNotificationSender
AgentRegistry
认证与授权
TLS
超时 / Retry
服务发现
```

AgentRegistry 还可以接 Nacos，这会在后面的企业基础设施课程里继续学习。

---

## 16. 本课最终模型

```text
                 A2A Client Side
                       │
                A2aAgent (Agent)
                       │
                AgentCardResolver
                       │
          GET /.well-known/agent-card.json
                       │
                       ▼
              ┌─────────────────┐
              │ A2A Server App  │
              ├─────────────────┤
              │ AgentCard       │
              │ JSON-RPC        │
              │ AgentRunner     │
              └────────┬────────┘
                       │
                       ▼
                   ReActAgent
```

下一课把 Agent 包装成另一种非常常见的标准：OpenAI Chat Completions API。
