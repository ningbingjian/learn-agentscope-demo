# 50-HookAndRuntimeExtension

## 本课目标

第 09 课学习了 Middleware 生命周期，第 45 课又看到 TrainingRunner 使用 System Hook。本课把三套经常混淆的机制放到一张图里：

```text
                    Agent Runtime
                         │
       ┌─────────────────┼────────────────┐
       │                 │                │
 Legacy Hook         Middleware       AgentEvent
       │                 │                │
兼容旧扩展          2.0 推荐扩展        对外事件流
       │                 │                │
 HookEvent         onAgent/...        SSE / UI / tracing consumer
```

## 一、最重要的 2.0.1 结论

`io.agentscope.core.hook.Hook` 在 AgentScope Java 2.0.1 已经：

```java
@Deprecated(forRemoval = true, since = "2.0.0")
```

官方 Javadoc 明确要求新扩展使用 `MiddlewareBase`。因此本课学习 Hook 的目的不是推荐新代码继续写 Hook，而是：

1. 看懂旧代码和 Training 等兼容机制；
2. 理解 System Hook 的全局注入语义；
3. 明确为什么 Middleware 是 2.0 的长期扩展点。

## 二、Middleware

Middleware 提供五个入口：

```text
onAgent
onReasoning
onActing
onModelCall
onSystemPrompt
```

前四个使用 onion pattern：

```text
Middleware A before
  ↓
Middleware B before
  ↓
core execution
  ↓
Middleware B after
  ↓
Middleware A after
```

`onSystemPrompt` 则是 pipeline transform。

本课 `CountingMiddleware` 同时计数：

```text
agent
reasoning
modelCall
systemPrompt
```

并在系统提示词末尾加：

```text
[middleware-marker]
```

`HookDemoModel` 会记录它真正收到的 SYSTEM message，因此可以验证变换不是“只改了一个本地变量”，而是真的进入 Model 调用。

## 三、Middleware order

2.0.1 的规则是：**order 数字越大，越靠洋葱外层，before 阶段越先执行。**

不要把它和 legacy Hook 的 priority 规则混淆：

```text
Middleware.order():   bigger -> outer / before earlier
Hook.priority():      smaller -> earlier
```

这是两个不同系统。

## 四、Legacy Hook

Hook 使用统一：

```java
<T extends HookEvent> Mono<T> onEvent(T event)
```

历史上可看到：

```text
PreCallEvent / PostCallEvent
PreReasoningEvent / PostReasoningEvent
PreActingEvent / PostActingEvent
ReasoningChunkEvent / ActingChunkEvent
ErrorEvent
```

部分事件有 setter，可以修改执行上下文；部分仅用于通知。

本模块保留 `LegacyCountingHook`，但类注释明确标注 compatibility-only。

## 五、System Hook 到底是什么

AgentBase 内部存在静态：

```text
systemHooks: CopyOnWriteArrayList<Hook>
```

构造 AgentBase 时执行：

```text
instance hooks
   +
systemHooks snapshot
   ↓
sort by Hook.priority
```

因此它的关键语义是：

```text
AgentBase.addSystemHook(h)
        ↓
此后构造 Agent A
        ↓
A 内部复制 h
        ↓
AgentBase.removeSystemHook(h)
        ↓
此后构造 Agent B 不再带 h

但是 A 已经复制进去的 h 不会被反向删除
```

本课测试直接证明这个行为。

## 六、为什么 TrainingRunner 会用 System Hook

Training 需要在一批 Agent 构造时全局挂入 sampling/router，而不是要求业务开发者为每个 Agent 手工配置旧 Hook，因此兼容层仍有价值。但对于自己的新业务扩展，应优先：

```text
业务级拦截/变换   -> Middleware
对外增量输出       -> AgentEvent
旧框架兼容/特定全局设施 -> 理解 Hook/System Hook，但谨慎新增
```

## 七、Hook 与 AgentEvent 不一样

`HookEvent` 是旧内部 interception event；`AgentEvent` 是当前运行时向外暴露的流式事件。

```text
HookEvent
= 执行链内部的 legacy interception model

AgentEvent
= TextBlockDelta / ToolCallStart / AgentEnd 等可观察事件
```

前端 SSE、AG-UI、OpenTelemetry 消费的思路应围绕 AgentEvent/Middleware，而不是新写 Hook。

## 八、一步步编码

### Step 1：写 deterministic Model

模型只返回 `runtime-extension-ok`，并记录 SYSTEM prompt。

### Step 2：写 CountingMiddleware

实现 `onAgent/onReasoning/onModelCall/onSystemPrompt`。

### Step 3：接入 ReActAgent

```java
ReActAgent.builder()
    .model(model)
    .middleware(middleware)
    .build();
```

### Step 4：保留一个 LegacyCountingHook

只用于 contract test 与版本迁移学习。

### Step 5：测试 System Hook 构造时复制语义

测试必须 `finally removeSystemHook`，否则静态全局状态会污染其他测试。

## 九、接口实验

```bash
./mvnw -pl 50-HookAndRuntimeExtension spring-boot:run
```

查看版本契约：

```bash
curl http://localhost:18081/api/runtime-extension/contract
```

执行 Middleware 链：

```bash
curl -X POST http://localhost:18081/api/runtime-extension/call \
  -H 'Content-Type: application/json' \
  -d '{"message":"hello"}'
```

## 十、测试

```bash
./mvnw -pl 50-HookAndRuntimeExtension test
```

覆盖：

- Middleware 四个关键入口真实触发
- system prompt transform 真进入 Model
- Hook deprecation metadata
- local Hook 兼容执行
- System Hook 在 Agent 构造时复制、remove 不影响已构造 Agent

## 十一、生产建议

```text
新项目：Middleware first
旧 Hook：迁移/兼容理解
System Hook：只用于确实需要全局注入的基础设施，并严格控制生命周期
AgentEvent：用于观察与 UI/协议输出
```

这一课结束后，第 09、24、45 中零散出现的运行时扩展机制就能统一起来。
