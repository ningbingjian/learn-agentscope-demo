# 07-StructuredOutput

本节学习 AgentScope Java 2.x 的 **Structured Output（结构化输出）**：让 Agent 最终返回一个可以直接转换成 Java 类型的结构化结果，而不是让业务代码再去解析自然语言。

第 06 节解决了“运行中的 Agent 怎么停止”；第 07 节开始解决另一个真实业务问题：

> Agent 已经给出答案了，后端程序怎样稳定地消费这个答案？

## 学习目标

完成本节后，你应该能够理解：

- 自由文本为什么不适合作为稳定的系统间接口。
- `call(msgs, structuredOutputClass, RuntimeContext)` 的作用。
- Java `record` 如何充当结构化输出契约。
- `Msg#hasStructuredData()` 与 `Msg#getStructuredData()` 的用途。
- AgentScope 如何把结构化结果存进 `Msg.metadata`。
- 原生 structured output 与 `generate_response` 降级路径的区别。
- Structured Output 与普通 Tool Calling 的职责差异。

## 为什么需要结构化输出

普通 Agent 最常见的结果是自然语言：

```text
用户：我的订单扣款了，但是一直没有到账。

Agent：
这是一个支付相关的高优先级问题，建议人工进一步处理。
```

人可以看懂，但后端程序很难可靠地消费：

```java
String answer = reply.getTextContent();

// 接下来怎么办？
// contains("高优先级")？
// 正则？
// 再调用一次模型解析？
```

这些方式都不稳定。

业务系统更希望得到：

```json
{
  "category": "PAYMENT",
  "priority": "HIGH",
  "summary": "用户扣款后订单未到账",
  "needHuman": true
}
```

这样 Controller、工作流、数据库和前端都可以继续使用明确字段。

## 本节的数据契约

定义一个普通 Java `record`：

```java
public record TicketAnalysis(
        String category,
        String priority,
        String summary,
        boolean needHuman
) {
}
```

可以把它理解成：

```text
TicketAnalysis.class
        ↓
结构化输出 Schema
        ↓
模型生成符合 Schema 的结果
        ↓
Msg.metadata
        ↓
getStructuredData(TicketAnalysis.class)
        ↓
TicketAnalysis Java 对象
```

这一点和传统后端 DTO 很像：**类型就是 Agent 与业务代码之间的契约。**

## 核心 API

AgentScope Java 2.0.1 的 `ReActAgent` 提供结构化输出重载：

```java
agent.call(
        List<Msg> msgs,
        Class<?> structuredOutputClass,
        RuntimeContext context
)
```

本案例使用：

```java
Msg reply = agent.call(
        List.of(new UserMessage(request.message())),
        TicketAnalysis.class,
        context
).block();
```

和普通调用最大的差别，就是多传了：

```java
TicketAnalysis.class
```

## 一次请求的完整流程

```text
POST /api/tickets/analyze
          ↓
Controller 校验参数
          ↓
构造 RuntimeContext
          ↓
new UserMessage(message)
          ↓
agent.call(..., TicketAnalysis.class, context)
          ↓
AgentScope 根据 TicketAnalysis 生成输出约束
          ↓
LLM 推理
          ↓
结构化结果写入 Msg.metadata
          ↓
reply.hasStructuredData()
          ↓
reply.getStructuredData(TicketAnalysis.class)
          ↓
Spring 返回稳定 JSON
```

## 工作原理

AgentScope 会根据模型能力选择不同实现路径，但调用方代码保持一致。

### 路径一：模型原生支持 Structured Output

部分模型可以直接接收 JSON Schema / response format：

```text
TicketAnalysis.class
        ↓
JSON Schema
        ↓
Model API response_format
        ↓
合法结构化结果
```

这种情况下模型直接按照 Schema 输出。

### 路径二：框架降级为 generate_response

如果模型没有原生结构化输出能力，AgentScope 可以通过一个内部合成工具完成：

```text
Agent
  ↓
generate_response
  ↓
按指定 Schema 提交字段
  ↓
AgentScope 提取成 structured data
```

因此业务代码不需要根据模型类型写两套逻辑。

## Structured Output 和 Tool Calling 的区别

这两个概念很容易混淆。

| 能力 | 作用 |
| --- | --- |
| Tool Calling | Agent 为了完成任务，调用外部能力 |
| Structured Output | Agent 最终以什么数据格式把结果交给程序 |

例如：

```text
用户：查一下订单 1001，然后判断是否需要人工处理

Agent
  ↓
调用 query_order 工具        ← Tool Calling
  ↓
获得订单数据
  ↓
继续分析
  ↓
TicketAnalysis              ← Structured Output
```

它们可以同时存在，并不冲突。

## 项目结构

```text
07-StructuredOutput
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/structuredoutput
    │   │   ├── StructuredOutputApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   ├── domain/TicketAnalysis.java
    │   │   └── web/TicketAnalysisController.java
    │   └── resources/application.yml
    └── test/java/com/example/agentscope/structuredoutput
        └── StructuredOutputContractTest.java
```

## 一步步实现

### 第一步：定义业务结果类型

```java
public record TicketAnalysis(
        String category,
        String priority,
        String summary,
        boolean needHuman
) {
}
```

这里故意不返回一个 `Map<String, Object>`，因为学习目标就是建立强类型契约。

### 第二步：创建 ReActAgent

```java
@Bean(destroyMethod = "close")
ReActAgent ticketAnalysisAgent(Model model) {
    return ReActAgent.builder()
            .name("ticket-analysis-agent")
            .sysPrompt("你是一个客服工单分析助手……")
            .model(model)
            .build();
}
```

本节没有工具，因此 AgentConfiguration 很简单。

### 第三步：构建 RuntimeContext

```java
RuntimeContext context = RuntimeContext.builder()
        .userId(request.userId())
        .sessionId(request.sessionId())
        .build();
```

Structured Output 本身和会话 ID 没有强绑定，但 Web 服务仍然显式传递上下文，保持前面课程的调用习惯。

### 第四步：告诉 AgentScope 最终类型

```java
Msg reply = agent.call(
        List.of(new UserMessage(request.message())),
        TicketAnalysis.class,
        context
).block();
```

这里的 `TicketAnalysis.class` 是本节最关键的一行。

### 第五步：从 Msg 中拿 Java 对象

```java
if (!reply.hasStructuredData()) {
    throw new IllegalStateException("Agent reply does not contain structured data");
}

TicketAnalysis analysis =
        reply.getStructuredData(TicketAnalysis.class);
```

注意：不是这样：

```java
// 不推荐
objectMapper.readValue(reply.getTextContent(), TicketAnalysis.class);
```

业务代码应该直接使用 AgentScope 提供的 structured data。

## 启动

在项目根目录执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 07-StructuredOutput spring-boot:run
```

服务端口：

```text
18081
```

## 测试接口

请求：

```bash
curl -X POST http://localhost:18081/api/tickets/analyze \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"ticket-001",
    "message":"我的订单已经扣款 299 元，但是订单一直显示未支付，客服也还没有处理。"
  }'
```

返回结果类似：

```json
{
  "userId": "alice",
  "sessionId": "ticket-001",
  "generateReason": "MODEL_STOP",
  "analysis": {
    "category": "PAYMENT",
    "priority": "HIGH",
    "summary": "用户已扣款但订单仍显示未支付",
    "needHuman": true
  }
}
```

模型输出存在一定语义差异，但 JSON 字段结构应该稳定。

## 自动化测试

本模块的单元测试不调用 DashScope，而是直接构造一份 AgentScope structured metadata：

```text
Map structured data
      ↓
Msg.metadata
      ↓
getStructuredData(TicketAnalysis.class)
      ↓
TicketAnalysis
```

运行：

```bash
./mvnw -pl 07-StructuredOutput test
```

它验证的是 Java 类型转换契约，不依赖网络和模型输出，因此结果稳定。

## 常见误区

### 误区一：Structured Output 就是让 Prompt 写“请输出 JSON”

不是。

```text
Prompt：请输出 JSON
```

仍然属于自然语言约束，模型可能加 Markdown、解释文本或字段变化。

Structured Output 的重点是把 **Schema** 作为执行约束交给框架和模型。

### 误区二：有 Structured Output 就不需要 Tool

两者解决不同问题。

```text
Tool       = Agent 怎么获取/修改外部世界
Structured = Agent 最后怎么把结果交给程序
```

### 误区三：所有业务都应该结构化

聊天、创作、解释型回答通常直接使用文本更自然。

分类、抽取、表单、工作流路由、后端接口结果则非常适合 Structured Output。

## 本节边界

本节只学习：

```text
Java 类型
   ↓
Structured Output
   ↓
Msg structured data
   ↓
业务 JSON
```

暂不展开：

- Tool + Structured Output 的组合案例
- 动态 JsonNode Schema
- Schema 校验细节
- 重试与容错策略
- HITL

下一节 `08-PermissionHITL` 会解决：**当 Agent 想执行有副作用的工具时，如何先暂停并让用户确认。**

## 延伸阅读

- AgentScope Java：Agent / Structured Output
  https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html
