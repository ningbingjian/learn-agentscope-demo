# 24-ObservabilityAndTracing：日志、指标与 OpenTelemetry Trace

## 1. 为什么 Agent 比普通 REST 服务更需要可观测性

普通请求通常是：

```text
HTTP → Service → DB → Response
```

Agent 一次请求可能是：

```text
HTTP
 ↓
Agent
 ↓
Model Call #1
 ↓
Tool A
 ↓
Model Call #2
 ↓
Tool B
 ↓
Model Call #3
 ↓
Final Answer
```

只看“接口耗时 8 秒”完全不够。

你还要知道：

```text
模型调用了几次？
哪个 Tool 最慢？
有没有重复 reasoning？
Token 用了多少？
错误发生在模型还是工具？
一个子 Agent 的调用属于哪条父链路？
```

---

## 2. 本节目标

学完应能解释：

- AgentScope Harness 默认 `AgentTraceMiddleware` 能看到什么；
- INFO 与 DEBUG trace 日志有什么差别和安全风险；
- 如何用自定义 Middleware 聚合调用次数和耗时；
- `OtelTracingMiddleware` 产生哪些标准 span；
- 为什么 Reactor 异步链路需要上下文传播；
- 没有 OTel SDK 时为什么 tracing middleware 近似 no-op；
- Collector / Jaeger / Tempo / OTLP 在生产中处于哪一层；
- 日志、Metrics、Trace 三者应该如何配合。

---

## 3. 三层可观测性

```text
                HarnessAgent
                    │
      ┌─────────────┼──────────────┐
      ↓             ↓              ↓
AgentTrace      Metrics        OTel Trace
Middleware      Middleware      Middleware
      │             │              │
      ↓             ↓              ↓
   Logs          Counters        Spans
                                  │
                         invoke_agent
                         chat <model>
                         execute_tool
```

### Logs

回答：

```text
刚才发生了什么？
```

### Metrics

回答：

```text
整体发生了多少次？快不快？失败率多少？
```

### Trace

回答：

```text
这一次请求内部到底经过了哪些步骤？父子关系是什么？
```

---

## 4. Harness 自带 AgentTraceMiddleware

HarnessAgent 2.0.1 默认已经开启执行 trace 日志。

本节显式写：

```java
.enableAgentTracingLog(true)
```

方便看到这个配置入口。

INFO 级别会记录类似：

```text
PRE_CALL
PRE_REASONING
POST_REASONING
PRE_ACTING
POST_ACTING
POST_CALL
```

DEBUG 级别还会记录更详细的：

```text
模型输入
reasoning text
Tool arguments
Tool result
```

### 安全提醒

生产环境不要无脑开 DEBUG。

Tool 参数/结果可能包含：

```text
用户数据
文件内容
业务参数
访问令牌
内部路径
```

可观测性本身也需要数据分级和脱敏。

---

## 5. 自定义 Metrics Middleware

本案例增加：

```text
ObservabilityMetricsMiddleware
```

统计：

```text
agentCalls
successfulCalls
failedCalls
reasoningRounds
modelCalls
actingRounds
totalLatencyMillis
maxLatencyMillis
```

核心思想：

```java
long started = System.nanoTime();

return next.apply(input)
        .doOnComplete(successfulCalls::increment)
        .doOnError(error -> failedCalls.increment())
        .doFinally(signal -> recordLatency(started));
```

注意：这不是为了替代 Micrometer/Prometheus。

它只是先让你看到：

```text
Middleware 生命周期
        ↓
可以天然生成可观测指标
```

生产中再把这些计数接到 Micrometer 即可。

---

## 6. OpenTelemetry Middleware

AgentScope Core 2.0.1 提供：

```java
new OtelTracingMiddleware()
```

它会生成三类 span：

```text
invoke_agent <agent-name>
    │
    ├── chat <model-name>
    │
    └── execute_tool <tool-name>
```

典型属性包括：

```text
gen_ai.operation.name
gen_ai.agent.name
gen_ai.agent.id
gen_ai.request.model
gen_ai.request.messages.count
gen_ai.request.tools.count
gen_ai.usage.input_tokens
gen_ai.usage.output_tokens
gen_ai.tool.name
gen_ai.tool.call.id
```

这已经开始接近标准 GenAI Observability。

---

## 7. 为什么默认不要求 Collector

`OtelTracingMiddleware` 只依赖 OpenTelemetry API。

如果没有 SDK：

```text
span = no-op
```

不会因为没有 Jaeger/Tempo 就把 Agent 跑挂。

本案例为了方便学习，提供可选的 Logging Exporter：

```bash
export OTEL_DEMO_ENABLED=true
```

开启后：

```text
OtelTracingMiddleware
        ↓
OpenTelemetrySdk
        ↓
SimpleSpanProcessor
        ↓
LoggingSpanExporter
        ↓
控制台
```

不用先搭 Collector 就能看到 span。

---

## 8. 一步步编码

### Step 1：创建业务 Tool

```text
service_status(component)
```

这是为了让一次 Agent 调用既可能出现：

```text
chat span
```

也可能出现：

```text
execute_tool span
```

### Step 2：注册 Metrics Middleware

```java
.middleware(metricsMiddleware)
```

### Step 3：打开 Harness trace log

```java
.enableAgentTracingLog(true)
```

### Step 4：注册 OTel tracing

```java
.middleware(new OtelTracingMiddleware())
```

### Step 5：可选安装 SDK

```text
OTEL_DEMO_ENABLED=true
```

通过 `OpenTelemetryDemoConfiguration` 注册一个 global SDK 和 logging exporter。

---

## 9. REST 实验

启动：

```bash
./mvnw -pl 24-ObservabilityAndTracing spring-boot:run
```

普通调用：

```bash
curl -X POST http://localhost:18081/api/observability/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"trace-1",
    "message":"请调用 service_status 检查 payment-service，然后告诉我结果"
  }'
```

查看聚合指标：

```bash
curl http://localhost:18081/api/observability/metrics
```

重置：

```bash
curl -X POST http://localhost:18081/api/observability/metrics/reset
```

---

## 10. 观察 OTel spans

开启：

```bash
export OTEL_DEMO_ENABLED=true
./mvnw -pl 24-ObservabilityAndTracing spring-boot:run
```

再次调用 Agent。

你应该能在控制台找到类似 span：

```text
invoke_agent observable-agent
chat qwen-plus
execute_tool service_status
```

具体是否出现 tool span 取决于模型是否实际调用了 `service_status`。

---

## 11. 生产架构

真正生产中通常不是 Logging Exporter：

```text
AgentScope
   ↓
OtelTracingMiddleware
   ↓
OpenTelemetry SDK
   ↓ OTLP
OpenTelemetry Collector
   ├── Tempo / Jaeger
   ├── Prometheus
   ├── Loki / Elasticsearch
   └── APM 平台
```

然后通过统一 traceId 把：

```text
HTTP Request
Agent
Model
Tool
DB
Remote Agent
```

串起来。

---

## 12. 和第 09 课的区别

第 09 课：

```text
Middleware 是怎么工作的？
```

第 24 课：

```text
怎么利用 Middleware 建立生产可观测性？
```

所以不是重复课程，而是同一个扩展机制的工程化应用。

---

## 13. 自动化测试

测试使用 FakeModel，不调用 DashScope。

验证一次 call 后：

```text
agentCalls = 1
successfulCalls = 1
modelCalls >= 1
reasoningRounds >= 1
totalLatency >= 0
```

`OtelTracingMiddleware` 在没有额外 SDK 的测试进程中仍应安全工作。

执行：

```bash
./mvnw -pl 24-ObservabilityAndTracing test
```

---

## 14. 本节边界

本节不搭建：

- Prometheus；
- Grafana；
- Jaeger；
- Tempo；
- OTel Collector；
- 企业告警规则。

重点是先把 AgentScope 的可观测接口和正确心智模型建立起来。

完成本节后，01～24 已经从“调用 Agent”一路推进到了“生产级 Agent 服务核心架构”。
