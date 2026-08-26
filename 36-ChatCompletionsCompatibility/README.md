# 36-ChatCompletionsCompatibility

本节学习 AgentScope Java 2.0.1 的 **OpenAI Chat Completions 兼容 Web API**。

目标不是再学习一种新的 Agent 推理模式，而是解决兼容问题：

> 已经有大量客户端只认识 `/v1/chat/completions`，怎么让它们不改 SDK 就能调用 AgentScope Agent？

本模块使用本地 `ChatCompatDemoModel`，不需要任何外部模型 API Key。

---

## 1. 这一课解决什么问题

假设已有系统代码：

```text
OpenAI SDK
LangChain OpenAI client
自研 OpenAI-compatible client
IDE Plugin
curl /v1/chat/completions
```

这些客户端期望：

```text
POST /v1/chat/completions
```

请求：

```json
{
  "model": "some-model",
  "messages": [
    {"role":"user","content":"hello"}
  ]
}
```

而你的后端实际上想运行：

```text
ReActAgent
```

Chat Completions Web 扩展充当兼容层：

```text
OpenAI-compatible Client
          ↓
/v1/chat/completions
          ↓
ChatCompletionsController
          ↓
ChatMessageConverter
          ↓
ReActAgent
          ↓
ChatCompletionsResponseBuilder
          ↓
OpenAI-compatible Response
```

---

## 2. 和前面协议的区别

到这一课，协议体系已经比较完整。

### AG-UI

```text
Agent ↔ UI
```

强调：

```text
RUN_STARTED
TEXT_MESSAGE_CONTENT
TOOL_CALL_*
interrupt / resume
```

### Agent Protocol

```text
System ↔ Agent Task Service
```

强调任务生命周期。

### A2A

```text
Agent ↔ Agent
```

强调 Agent 互操作。

### Chat Completions Compatibility

```text
OpenAI-compatible Client ↔ AgentScope
```

强调生态兼容。

---

## 3. 正确 Maven 依赖

AgentScope 2.0.1 的 Spring Boot artifact：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-chat-completions-web-starter</artifactId>
</dependency>
```

它内部继续依赖：

```text
agentscope-extensions-chat-completions-web
agentscope-spring-boot-starter
spring-boot-starter-web
spring-boot-starter-validation
```

本课仍然显式引入 `agentscope-core`，让课程的直接 AgentScope 依赖清晰可见。

---

## 4. 最重要的边界：它是 Stateless API

这是本课最重要的知识点。

2.0.1 的 `ChatCompletionsWebAutoConfiguration` 和 `ChatCompletionsController` 明确按照：

```text
100% stateless
```

设计。

也就是说它不是：

```text
request 1
   ↓
server session memory
   ↓
request 2 自动记住
```

而是：

```text
request 1
messages = [user1]
   ↓
response assistant1

client 保存历史
   ↓
request 2
messages = [user1, assistant1, user2]
```

完整历史由 **客户端** 负责。

---

## 5. 为什么每次请求要 fresh ReActAgent

Starter 内部持有：

```java
ObjectProvider<ReActAgent>
```

每个 HTTP 请求：

```java
ReActAgent agent = agentProvider.getObject();
```

因此本课主动把 Agent 声明成：

```java
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
ReActAgent chatCompletionsAgent(...) {
    ...
}
```

完整模型：

```text
HTTP Request A
     ↓
ReActAgent #1
     ↓
结束

HTTP Request B
     ↓
ReActAgent #2
     ↓
结束
```

这样单次 request 的临时 Toolkit / Schema-only Tool 不会污染下一次请求。

---

## 6. 本模块结构

```text
36-ChatCompletionsCompatibility/
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/chatcompat
    │   │   ├── ChatCompletionsCompatibilityApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   ├── model/ChatCompatDemoModel.java
    │   │   └── web/CompatibilityInfoController.java
    │   └── resources/application.yml
    └── test
        └── java/com/example/agentscope/chatcompat/ChatCompletionsCompatibilityTest.java
```

---

## 7. HTTP 自动配置

配置：

```yaml
agentscope:
  chat-completions:
    enabled: true
    base-path: /v1/chat/completions
```

Starter 自动创建：

```text
ChatMessageConverter
OpenAIToolConverter
ChatCompletionsResponseBuilder
ChatCompletionsStreamingAdapter
ChatCompletionsStreamingService
ChatCompletionsController
```

所以业务代码不需要自己写 Controller。

---

## 8. 非流式调用

启动：

```bash
./mvnw -pl 36-ChatCompletionsCompatibility spring-boot:run
```

调用：

```bash
curl -X POST http://localhost:18081/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "model":"lesson-agent",
    "messages":[
      {"role":"user","content":"你好"}
    ],
    "stream":false
  }'
```

响应使用 OpenAI Chat Completions 风格：

```json
{
  "id":"...",
  "object":"chat.completion",
  "choices":[
    {
      "index":0,
      "message":{
        "role":"assistant",
        "content":"Chat Completions demo reply: 你好"
      }
    }
  ]
}
```

---

## 9. 流式调用

同一个 URL：

```text
POST /v1/chat/completions
```

只需要：

```json
"stream": true
```

Controller 会自动切换为：

```text
text/event-stream
```

示例：

```bash
curl -N -X POST http://localhost:18081/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "model":"lesson-agent",
    "messages":[
      {"role":"user","content":"stream me"}
    ],
    "stream":true
  }'
```

内部关键类：

```text
ChatCompletionsStreamingAdapter
          ↓
ChatCompletionsStreamingService
          ↓
SSE chunks
```

---

## 10. Messages 怎么转换

客户端发送：

```text
system
user
assistant
tool
```

`ChatMessageConverter` 转换成 AgentScope：

```text
SystemMessage
UserMessage
AssistantMessage
TOOL Msg
```

然后一次性调用：

```java
agent.call(messages)
```

所以这套接口天然符合：

```text
客户端提供完整上下文
```

---

## 11. Tool Schema 兼容

OpenAI 请求还可以带：

```json
{
  "tools":[
    {
      "type":"function",
      "function":{
        "name":"get_weather",
        "parameters":{...}
      }
    }
  ]
}
```

Starter 会：

```text
OpenAI tools
    ↓
OpenAIToolConverter
    ↓
ToolSchema
    ↓
SchemaOnlyTool
    ↓
当前 fresh Agent Toolkit
```

这里非常关键：

```text
SchemaOnlyTool
```

意味着兼容接口可以把 Tool 声明交给 Agent，但具体外部执行通常仍由客户端完成。

流程类似：

```text
Agent 返回 tool_calls
        ↓
Client 执行函数
        ↓
Client 加入 tool message
        ↓
下一次完整 messages 再提交
```

这和 OpenAI Chat Completions 原有的 Tool Calling 模型一致。

---

## 12. 和 Harness server-side session 对比

### Gateway / Harness

```text
userId + sessionId
      ↓
Server-side AgentState
```

客户端可以只发送新的消息。

### Chat Completions

```text
No server session
      ↓
Client sends full messages every request
```

所以不要设计成：

```text
第一次 /v1/chat/completions 告诉名字
第二次只问“我叫什么？”
```

如果第二次请求没有把第一轮历史带回来，服务端没有义务记住。

---

## 13. 为什么需要这种兼容层

它特别适合：

```text
已有 OpenAI SDK 的系统
已有大模型网关
IDE / CLI
统一 LLM Client
企业内部 model abstraction
```

迁移前：

```text
Client → OpenAI-compatible LLM
```

迁移后：

```text
Client → AgentScope Chat Completions Adapter → Agent
```

客户端改动很小。

---

## 14. 本课的 Agent 创建计数器

为了让 stateless 不停留在文字描述，本模块增加：

```text
AtomicInteger agentCreationCounter
```

prototype Bean 每创建一个 Agent：

```java
counter.incrementAndGet();
```

辅助接口：

```text
GET /api/chat-compat/info
```

返回：

```json
{
  "stateless": true,
  "agentScope": "prototype per request",
  "agentsCreated": 3
}
```

多调用几次 `/v1/chat/completions` 就能直接看到数字增加。

---

## 15. 自动化测试

```bash
./mvnw -pl 36-ChatCompletionsCompatibility test
```

第一个测试使用 MockMvc 真请求：

```text
POST /v1/chat/completions
```

验证：

```text
HTTP 200
choices[0].message.role = assistant
choices[0].message.content = deterministic reply
```

第二个测试连续请求两次，然后验证：

```text
agentCreationCounter + 2
```

也就是说它不是“文档说 fresh agent”，而是测试直接证明每请求一份 prototype Agent。

---

## 16. 四类接口最终对比

| 接口 | 主要消费者 | 状态模型 | 重点 |
|---|---|---|---|
| AG-UI | 前端 UI | thread/run | UI events |
| Agent Protocol | 业务系统 | task | task lifecycle |
| A2A | 其他 Agent | A2A task/message | interoperability |
| Chat Completions | OpenAI-compatible client | client-side history | compatibility |

---

## 17. 本课最终模型

```text
OpenAI Compatible Client
          │
          │ messages = FULL HISTORY
          ▼
POST /v1/chat/completions
          │
          ▼
ChatCompletionsController
          │
          ├── ChatMessageConverter
          ├── OpenAIToolConverter
          │
          ▼
ObjectProvider<ReActAgent>
          │
          ▼
Fresh Prototype Agent
          │
          ▼
        Model
          │
          ▼
ChatCompletionsResponseBuilder
          │
          ▼
OpenAI Compatible JSON / SSE
```

至此，AgentScope Java 2.0.1 的主要标准协议入口已经形成完整体系。
