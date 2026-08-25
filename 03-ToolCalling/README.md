# 03-ToolCalling

本节通过一个可精确计算小数的 Java 工具，学习 `ReActAgent` 如何识别任务、选择工具、构造参数，并根据工具结果生成最终回复。

```text
用户问题 -> 模型判断需要计算 -> calculate 工具 -> 计算结果 -> 模型组织回复
```

## 学习目标

完成本节后，你应该能够理解：

- Tool 为什么能扩展大模型的能力边界。
- `@Tool` 和 `@ToolParam` 如何把 Java 方法变成模型可识别的工具。
- `Toolkit` 在工具注册、Schema 暴露和调用分发中的作用。
- Agent 如何完成“推理 -> 调用 -> 观察 -> 再推理”的 ReAct 循环。
- 工具返回值与 HTTP 最终回复之间的区别。

## 理论基础

### Tool 解决什么问题

大模型擅长理解语言和进行推理，但它不能天然保证精确计算，也无法凭空读取数据库、调用业务接口或执行程序。

Tool 把这些确定性能力提供给 Agent：

| 能力 | 适合的执行者 |
| --- | --- |
| 理解“用户想要什么” | 大模型 |
| 判断“应该使用哪个工具” | ReActAgent + 大模型 |
| 精确执行数学计算 | Java 计算工具 |
| 把结果解释给用户 | 大模型 |

本节使用 `BigDecimal` 执行计算，让 Java 代码负责确定性结果，模型只负责选择工具和解释结果。

### Tool 不是模型直接调用 Java 方法

模型看不到 Java 对象，它看到的是 AgentScope 根据注解生成的 JSON Schema。以本节工具为例，Schema 表达的信息大致是：

```json
{
  "name": "calculate",
  "description": "Precisely calculates two decimal numbers...",
  "parameters": {
    "left": "decimal",
    "operation": "add | subtract | multiply | divide",
    "right": "decimal"
  }
}
```

模型产生一个结构化的工具调用意图，例如：

```json
{
  "name": "calculate",
  "arguments": {
    "left": 123.45,
    "operation": "multiply",
    "right": 67.89
  }
}
```

AgentScope 再把 JSON 参数转换成 Java 参数，调用真正的 `calculate()` 方法。

### ReAct 工具调用循环

```text
用户：请计算 123.45 × 67.89
                 ↓
模型：这是精确计算，需要 calculate
                 ↓
AgentScope：执行 CalculatorTools.calculate(...)
                 ↓
工具结果：8381.0205
                 ↓
工具结果作为新消息交还模型
                 ↓
模型：生成面向用户的最终回复
```

所以，Tool 的返回值并不是 Controller 直接返回的结果。它先成为 Agent 的“观察”，模型阅读它以后才产生最终 `Msg`。

### Toolkit 的作用

`Toolkit` 是 Agent 的工具容器，负责：

- 扫描对象上的 `@Tool` 方法。
- 生成并管理工具 JSON Schema。
- 将 Schema 暴露给模型。
- 收到工具调用后，分发到对应的 Java 对象。
- 将 Java 返回值转换成 AgentScope 的工具结果消息。

只是创建 `CalculatorTools` 对象还不够；必须将它注册到 `Toolkit`，再把 `Toolkit` 交给 Agent。

## 项目结构

```text
03-ToolCalling
├── pom.xml
└── src
    ├── main
    │   ├── java/com/example/agentscope/toolcalling
    │   │   ├── ToolCallingApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   ├── tool/CalculatorTools.java
    │   │   └── web/ChatController.java
    │   └── resources/application.yml
    └── test/java/com/example/agentscope/toolcalling/tool
        └── CalculatorToolsTest.java
```

## 核心代码

### 1. 定义 Java Tool

```java
@Tool(
        name = "calculate",
        description = "Precisely calculates two decimal numbers...",
        strict = true,
        readOnly = true,
        concurrencySafe = true
)
public String calculate(
        @ToolParam(name = "left", description = "The left decimal operand") BigDecimal left,
        @ToolParam(name = "operation", description = "One of: add, subtract, multiply, divide")
        String operation,
        @ToolParam(name = "right", description = "The right decimal operand") BigDecimal right
) {
    // 使用 BigDecimal 执行真正计算
}
```

注解属性的作用：

| 属性 | 作用 |
| --- | --- |
| `name` | 模型调用时使用的工具名称 |
| `description` | 告诉模型什么时候应该使用它 |
| `strict` | 要求支持的模型严格按 Schema 生成参数 |
| `readOnly` | 表明工具不修改外部状态 |
| `concurrencySafe` | 表明工具可以安全并发调用 |

`@ToolParam` 的 `name` 必须明确填写，因为 Java 编译后不一定保留方法参数名。

### 2. 注册到 Toolkit

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(calculatorTools);
```

`registerTool(Object)` 会反射扫描该对象中的 `@Tool` 方法。本节只有一个 `calculate` 工具。

### 3. 把 Toolkit 交给 ReActAgent

```java
return ReActAgent.builder()
        .name("tool-calling-agent")
        .sysPrompt("你是一个严谨的计算助手……")
        .model(model)
        .toolkit(toolkit)
        .build();
```

System Prompt 要求 Agent 在遇到算术问题时使用工具。工具的 `description` 描述工具自身的能力，System Prompt 则规定 Agent 整体的行为策略。

## 启动

确认 `src/main/resources/application-local.yml` 已填写测试 Key；首次克隆时可以复制同目录的 `application-local.example.yml`。

在项目根目录中执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -pl 03-ToolCalling spring-boot:run
```

服务端口是 `18081`。

## 测试工具调用

```bash
curl -X POST http://localhost:18081/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"请使用计算工具计算 123.45 乘以 67.89"}'
```

预期最终结果包含：

```text
8381.0205
```

同时，服务端控制台应该出现类似日志：

```text
Tool calculate called: left=123.45, operation=multiply, right=67.89, result=8381.0205
```

这条日志是 Java 方法真正被执行的直接证据。

## 测试不调用工具

```bash
curl -X POST http://localhost:18081/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"请用一句话说明什么是 Tool Calling"}'
```

这个问题不需要计算，控制台不应出现新的 `Tool calculate called` 日志。这说明代码没有在 Controller 中硬编码调用工具，而是 Agent 根据任务自主选择。

## 错误边界

- 除数为 `0` 时，工具抛出 `Divisor must not be zero`。
- `operation` 不在允许列表时，工具拒绝执行。
- 工具执行错误会作为观察交还 Agent，Agent 可以换参数重试，或向用户解释错误。

## 本节边界

本节只学习本地 Java Tool 的定义、注册和 ReAct 调用循环，不使用 MCP、外部 HTTP API、会话记忆、流式输出、工具分组或人工审批。

## 延伸阅读

- [AgentScope Java：Tool](https://java.agentscope.io/v2/zh/docs/building-blocks/tool.html)
- [AgentScope Java：Agent](https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html)
