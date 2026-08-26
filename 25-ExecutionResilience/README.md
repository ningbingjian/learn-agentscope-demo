# 25-ExecutionResilience：超时、重试与指数退避

## 1. 为什么 Agent 到生产环境必须有执行策略

开发环境里最容易写成：

```text
Agent -> Model -> Tool -> 等到成功
```

生产环境却一定会遇到：

```text
模型 429
模型 5xx
网络抖动
模型一直不返回
Tool 卡死
Tool 调外部系统超时
```

如果没有明确策略，请求可能无限占用线程、连接和会话槽位。

本节只解决一个问题：**一次 Model / Tool 执行失败时，系统应该等多久、要不要重试、多久后再重试。**

---

## 2. 学习目标

学完应能解释：

- `ExecutionConfig` 是什么；
- Model 与 Tool 为什么使用不同策略；
- `timeout` 和整个 Agent 请求超时有什么区别；
- `maxAttempts=3` 为什么表示 1 次初始调用 + 2 次 retry；
- exponential backoff 的作用；
- 哪些错误适合 retry；
- 为什么有副作用的 Tool 默认不应该自动 retry；
- AgentScope 默认 Model / Tool 策略分别是什么；
- agent-level 配置怎样传到实际 Model/Tool 调用。

---

## 3. AgentScope 2.0.1 的默认策略

`ExecutionConfig` 同时服务 Model 和 Tool。

官方默认：

```text
MODEL_DEFAULTS
├── timeout = 5 min
├── maxAttempts = 3
├── initialBackoff = 2 s
├── maxBackoff = 30 s
└── retry = 429 / 5xx / timeout / IO

TOOL_DEFAULTS
├── timeout = 5 min
└── maxAttempts = 1
```

最值得注意的是：

```text
Model 默认允许 retry
Tool 默认不 retry
```

原因不是 Tool 不重要，而是 Tool 更可能产生副作用。

---

## 4. 为什么 Tool 不应该随便 retry

假设 Tool 是：

```text
refund_order(orderId)
```

第一次调用：

```text
退款成功
↓
网络在返回结果前断开
↓
Agent 认为失败
```

如果框架直接 retry：

```text
refund_order(orderId)
↓
第二次退款
```

因此生产设计需要先回答：

```text
这个 Tool 是否幂等？
```

只有满足幂等、幂等键或明确去重机制时，才适合配置自动重试。

---

## 5. 本节策略

Model：

```java
ExecutionConfig.builder()
    .timeout(Duration.ofSeconds(20))
    .maxAttempts(3)
    .initialBackoff(Duration.ofMillis(500))
    .maxBackoff(Duration.ofSeconds(3))
    .backoffMultiplier(2.0)
    .retryOn(ExecutionConfig.RETRYABLE_ERRORS)
    .build();
```

Tool：

```java
ExecutionConfig.builder()
    .timeout(Duration.ofSeconds(3))
    .maxAttempts(1)
    .build();
```

含义：

```text
Model
第一次失败
  ↓ 500ms
第二次失败
  ↓ ~1s
第三次

Tool
第一次失败
  ↓
直接把失败交回 Agent
```

---

## 6. 一步步编码

### Step 1：声明 Model 策略

```java
@Bean
ExecutionConfig modelExecutionConfig() {
    return ExecutionConfig.builder()
            .timeout(Duration.ofSeconds(20))
            .maxAttempts(3)
            .initialBackoff(Duration.ofMillis(500))
            .maxBackoff(Duration.ofSeconds(3))
            .backoffMultiplier(2.0)
            .retryOn(ExecutionConfig.RETRYABLE_ERRORS)
            .build();
}
```

`RETRYABLE_ERRORS` 在 2.0.1 中默认识别：

```text
HTTP 429
HTTP 5xx
TimeoutException
IOException
以及包装后的上述异常
```

400 / 401 / 403 等客户端错误不应该靠重试解决。

### Step 2：声明 Tool 策略

```java
@Bean
ExecutionConfig toolExecutionConfig() {
    return ExecutionConfig.builder()
            .timeout(Duration.ofSeconds(3))
            .maxAttempts(1)
            .build();
}
```

本节故意不打开 Tool retry。

### Step 3：挂到 Agent

```java
ReActAgent.builder()
        .model(model)
        .toolkit(toolkit)
        .modelExecutionConfig(modelExecutionConfig)
        .toolExecutionConfig(toolExecutionConfig)
        .build();
```

这里配置的是 Agent 级默认执行策略。

实际链路：

```text
Agent
 ↓
GenerateOptions / ToolExecutor
 ↓
ExecutionConfig
 ├── timeout
 ├── retry count
 ├── retry filter
 └── backoff
```

### Step 4：创建 slow_task

工具：

```text
slow_task(millis)
```

用于主动制造一个慢 Tool。

例如要求等待 5000ms，而 Tool timeout 是 3000ms，就能观察超时结果。

### Step 5：暴露策略观察接口

```text
GET /api/resilience/config
```

返回 Model / Tool 当前策略，避免配置变成“写完不知道到底生效没有”。

### Step 6：正常调用

```text
POST /api/resilience/chat
```

仍然使用 `RuntimeContext(userId, sessionId)` 保持会话隔离。

---

## 7. 启动

```bash
export DASHSCOPE_API_KEY="你的 Key"
./mvnw -pl 25-ExecutionResilience spring-boot:run
```

检查策略：

```bash
curl http://localhost:18081/api/resilience/config
```

预期核心字段：

```json
{
  "model": {
    "timeoutMillis": 20000,
    "maxAttempts": 3
  },
  "tool": {
    "timeoutMillis": 3000,
    "maxAttempts": 1
  }
}
```

---

## 8. Tool timeout 实验

```bash
curl -X POST http://localhost:18081/api/resilience/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"timeout-demo",
    "message":"请调用 slow_task 等待 5000 毫秒，并告诉我工具是否成功。"
  }'
```

因为：

```text
slow_task = 5s
Tool timeout = 3s
```

所以框架应该在 Tool 执行层得到超时错误，而不是一直等完整 5 秒后再认为正常。

---

## 9. 自动化测试

```bash
./mvnw -pl 25-ExecutionResilience test
```

`ExecutionResilienceTest` 使用一个 FlakyModel：

```text
attempt 1 -> failure
attempt 2 -> failure
attempt 3 -> success
```

测试同时验证：

```text
model maxAttempts = 3
tool maxAttempts = 1
最终回复 recovered
总尝试次数 = 3
```

测试不访问 DashScope。

---

## 10. timeout 的层次

不要把所有 timeout 混成一个。

生产系统通常至少有：

```text
HTTP Request timeout
        ↓
Agent total budget
        ↓
Model execution timeout
        ↓
Tool execution timeout
        ↓
Tool 内部下游 HTTP timeout
```

本节只负责中间两层中的 Model / Tool execution timeout。

---

## 11. retry 的生产原则

### Model

通常适合重试：

```text
429
5xx
网络抖动
连接重置
临时 timeout
```

通常不适合：

```text
401
403
参数错误
模型不存在
业务逻辑错误
```

### Tool

先问：

```text
是否 readOnly？
是否幂等？
是否有 idempotency key？
是否有去重表？
```

再决定 retry。

---

## 12. backoff 为什么必要

错误做法：

```text
429
↓ 立即 retry
429
↓ 立即 retry
429
```

这叫 retry storm。

指数退避：

```text
500ms
1s
2s
3s cap
```

让下游有恢复时间，也降低集体重试冲击。

实际实现通常还会加入 jitter，防止多个 Pod 同时重试。

---

## 13. 与前面课程关系

```text
03 ToolCalling
      ↓
25 Tool 不再只是“能调用”
   而是“受 timeout/retry 约束地调用”

05 Concurrency
      ↓
25 高并发失败时避免 retry storm

24 Observability
      ↓
25 timeout/retry 应进入 metrics / trace
```

下一课继续解决：

```text
服务要下线 / Pod 收到 SIGTERM 时
正在运行的 Agent 怎么办？
```

进入 `26-GracefulShutdownAndRecovery`。
