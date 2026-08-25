# 09-MiddlewareLifecycle

本节学习 AgentScope Java 2.x 的 **Middleware 生命周期**：如何在 Agent 的关键执行阶段统一插入日志、指标、审计、限流、成本统计或输入输出变换，而不把这些横切逻辑塞进 Controller、Tool 或业务 Prompt。

本案例实现：

```text
AgentExecutionLoggingMiddleware
```

它会记录并统计 AgentScope 的五个核心 middleware 入口：

```text
onAgent
onReasoning
onActing
onModelCall
onSystemPrompt
```

## 学习目标

完成本节后，你应该能够理解：

- Middleware 为什么属于 Agent 的“横切能力”。
- `MiddlewareBase` 提供的五个生命周期入口分别位于哪里。
- `next.apply(input)` 为什么是 middleware 链的关键。
- “洋葱模型” before / after 是如何形成的。
- `onSystemPrompt` 为什么和另外四个 hook 的形态不同。
- 为什么一次 Agent call 可能出现多次 reasoning / model call / acting。
- Middleware 和之前学习的 Event / Tool / Controller 有什么区别。
- 如何把日志、耗时和计数器做成一个可观察案例。

## 为什么需要 Middleware

没有 Middleware 时，我们可能到处写：

```java
long start = System.nanoTime();
log.info("start");

Msg reply = agent.call(...).block();

log.info("finished");
```

或者每个 Tool 都自己记录：

```java
log.info("tool started");
```

这会带来两个问题：

1. 横切逻辑散落在业务代码中。
2. 很难统一观察 Agent 内部的 reasoning、model call、acting。

Middleware 的目标就是：

```text
业务逻辑保持干净
       ↓
Middleware 统一包围执行过程
       ↓
日志 / 指标 / 审计 / 策略集中处理
```

## MiddlewareBase 的五个入口

AgentScope Java 2.0.1 中 `MiddlewareBase` 提供：

```java
onAgent(...)
onReasoning(...)
onActing(...)
onModelCall(...)
onSystemPrompt(...)
```

### onAgent

包围整个 Agent invocation：

```text
onAgent before
      ↓
整个 Agent call
      ↓
onAgent after
```

适合：

- 总耗时
- request trace
- 全局审计
- 调用级指标

### onReasoning

包围一次 reasoning 阶段：

```text
reasoning
   ↓
准备消息 / 工具 / options
   ↓
模型推理与输出解析
```

一次 ReAct call 可能多次 reasoning。

### onModelCall

更靠近真实模型 API：

```text
Agent reasoning
      ↓
onModelCall
      ↓
DashScope / OpenAI / ...
```

适合：

- 模型耗时
- token / cost 统计
- fallback 观察
- model request/response normalization

### onActing

Agent 决定调用 Tool 后进入 acting：

```text
模型产生 Tool Call
       ↓
onActing
       ↓
权限检查
       ↓
Tool 执行
```

适合：

- Tool 审计
- 执行耗时
- 工具策略
- 调用链追踪

### onSystemPrompt

它不是“包围执行”的 Flux hook，而是一个 prompt transformer：

```text
原始 system prompt
       ↓
Middleware A
       ↓
Middleware B
       ↓
最终 prompt
```

所以返回的是：

```java
Mono<String>
```

而不是 `Flux<AgentEvent>`。

## 洋葱模型

四个主要执行 middleware 都接收一个：

```java
next
```

最核心的写法是：

```java
log.info("before");

return next.apply(input)
        .doOnComplete(() -> log.info("after"));
```

可以想象成：

```text
Middleware A before
    ↓
Middleware B before
    ↓
Core Logic
    ↓
Middleware B after
    ↓
Middleware A after
```

这就是典型的 Onion Pattern。

## order() 的意义

本案例：

```java
@Override
public int order() {
    return 100;
}
```

AgentScope 约定：

```text
order 越大
    ↓
优先级越高
    ↓
越靠洋葱外层
```

如果以后有：

```text
TracingMiddleware  order=100
AuditMiddleware    order=50
CostMiddleware     order=10
```

before 顺序大致是：

```text
Tracing
  ↓
Audit
  ↓
Cost
  ↓
Core
```

结束时顺序反过来。

## 本案例做了什么

`AgentExecutionLoggingMiddleware` 除了日志，还维护五个计数器：

```text
agentCalls
reasoningCalls
actingCalls
modelCalls
systemPromptTransforms
```

请求完成后，Controller 会一起返回 snapshot。

因此你不仅能看日志，还能看到：

```json
{
  "agentCalls": 1,
  "reasoningCalls": 2,
  "actingCalls": 1,
  "modelCalls": 2,
  "systemPromptTransforms": 2
}
```

具体次数取决于模型和 ReAct 循环，不保证固定就是上面的数字。

## 为什么 reasoning / model call 可能大于 1

发送：

```text
请计算 17 * 23
```

典型过程：

```text
第 1 次 Reasoning
      ↓
第 1 次 Model Call
      ↓
模型决定调用 multiply
      ↓
Acting
      ↓
Tool result = 391
      ↓
第 2 次 Reasoning
      ↓
第 2 次 Model Call
      ↓
模型生成最终回答
```

因此：

```text
1 个 HTTP 请求
≠ 1 次模型调用
```

这是做 Agent 成本统计时非常重要的认识。

## 项目结构

```text
09-MiddlewareLifecycle
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/middlewarelifecycle
    │   │   ├── MiddlewareLifecycleApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   ├── middleware/AgentExecutionLoggingMiddleware.java
    │   │   ├── tool/MathTools.java
    │   │   └── web/MiddlewareController.java
    │   └── resources/application.yml
    └── test/java/com/example/agentscope/middlewarelifecycle
        └── AgentExecutionLoggingMiddlewareTest.java
```

## 一步步实现

### 第一步：创建 Middleware 类

```java
@Component
public class AgentExecutionLoggingMiddleware implements MiddlewareBase {
}
```

Middleware 是我们自己写的 Spring 组件，因此直接用 `@Component`。

### 第二步：实现 onAgent

```java
@Override
public Flux<AgentEvent> onAgent(
        Agent agent,
        RuntimeContext ctx,
        AgentInput input,
        Function<AgentInput, Flux<AgentEvent>> next
) {
    log.info("before");

    return next.apply(input)
            .doOnComplete(() -> log.info("after"));
}
```

如果忘记：

```java
next.apply(input)
```

那相当于 Middleware 把后面的执行链截断了。

### 第三步：实现其他生命周期入口

本案例继续实现：

```text
onReasoning
onActing
onModelCall
onSystemPrompt
```

但不修改输入，只观察。

这很适合作为第一节 Middleware 案例。

### 第四步：注册到 ReActAgent

```java
return ReActAgent.builder()
        .name("middleware-agent")
        .model(model)
        .toolkit(toolkit)
        .middleware(loggingMiddleware)
        .build();
```

核心就是：

```java
.middleware(loggingMiddleware)
```

### 第五步：加入一个 Tool 触发 Acting

如果只问：

```text
你好
```

通常不会进入 Tool Acting。

所以本案例加入：

```java
@Tool(name = "multiply")
```

让你用乘法问题明确观察：

```text
Reasoning → ModelCall → Acting → Tool → Reasoning → ModelCall
```

## 启动

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 09-MiddlewareLifecycle spring-boot:run
```

## 实验一：先清空统计

```bash
curl -X POST http://localhost:18081/api/middleware/metrics/reset
```

预期：

```json
{
  "agentCalls": 0,
  "reasoningCalls": 0,
  "actingCalls": 0,
  "modelCalls": 0,
  "systemPromptTransforms": 0
}
```

## 实验二：触发 Tool + ReAct 循环

```bash
curl -X POST http://localhost:18081/api/middleware/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"middleware-001",
    "message":"请计算 17 * 23"
  }'
```

返回类似：

```json
{
  "userId": "alice",
  "sessionId": "middleware-001",
  "generateReason": "MODEL_STOP",
  "reply": "17 × 23 = 391。",
  "lifecycle": {
    "agentCalls": 1,
    "reasoningCalls": 2,
    "actingCalls": 1,
    "modelCalls": 2,
    "systemPromptTransforms": 2
  }
}
```

实际计数可能因模型行为不同而略有变化。

同时观察 Spring Boot 日志，你会看到类似：

```text
middleware onAgent before
middleware onSystemPrompt
middleware onReasoning before
middleware onModelCall before
middleware onModelCall after
middleware onReasoning after
middleware onActing before
middleware onActing after
...
middleware onAgent after
```

## 实验三：只查看累计指标

```bash
curl http://localhost:18081/api/middleware/metrics
```

因为 Middleware Bean 是 Spring 单例，这些计数是当前进程启动后的累计值。

本节计数器只是教学观测手段，生产环境应该交给 Micrometer / OpenTelemetry 等监控体系，而不是自己用 `AtomicLong` 当正式指标平台。

## 自动化测试

测试直接调用五个 Middleware 入口，并把 `next` 替换成空的 Flux：

```text
onAgent       → count +1
onReasoning   → count +1
onActing      → count +1
onModelCall   → count +1
onSystemPrompt→ count +1
```

不需要 DashScope API Key。

运行：

```bash
./mvnw -pl 09-MiddlewareLifecycle test
```

## Middleware 和 Event 的区别

第 04 课学习了 AgentEvent。

两者关系可以简单理解为：

```text
Event
= Agent 执行过程中“发生了什么”

Middleware
= Agent 执行到某个阶段时“我可以包围、观察甚至修改它”
```

Event 更偏输出与观察；Middleware 更偏拦截与扩展。

## Middleware 和 Tool 的区别

```text
Tool
= Agent 主动选择执行的业务能力

Middleware
= 不需要模型主动选择，框架生命周期自动经过的横切逻辑
```

模型可以决定不调用 Tool，但只要 Agent 执行到对应阶段，Middleware 就会自动参与。

## Middleware 和 Controller 的区别

Controller 只能看到：

```text
HTTP request
      ↓
agent.call
      ↓
final result
```

Middleware 可以进入：

```text
Agent
Reasoning
Model Call
Acting
System Prompt
```

因此很多 Agent 专属观测逻辑放 Middleware 更合理。

## 后续能做什么

理解这节后，可以继续自己写：

```text
TracingMiddleware
CostMiddleware
AuditMiddleware
RateLimitMiddleware
PromptInjectionMiddleware
ModelFallbackMiddleware
```

但这一课只做日志与计数，不把概念一次堆太多。

## 本节边界

本节只学习：

```text
MiddlewareBase
      ↓
五个生命周期入口
      ↓
next.apply(input)
      ↓
洋葱模型
      ↓
日志 + 指标观察
```

暂不展开：

- OpenTelemetry
- Micrometer
- 自定义 model fallback
- 修改 ReasoningInput
- 修改模型输出事件
- 多 Middleware order 组合实验

到这里，07～09 三节组成了一条很完整的工程链：

```text
07 Structured Output
结果如何稳定交给程序
        ↓
08 Permission HITL
有副作用动作如何受控
        ↓
09 Middleware
整个 Agent 生命周期如何统一治理
```

## 延伸阅读

- AgentScope Java：MiddlewareBase 源码
  https://github.com/agentscope-ai/agentscope-java/blob/v2.0.1/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java
- AgentScope Java：Agent
  https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html
