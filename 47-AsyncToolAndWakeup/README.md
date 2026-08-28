# 47-AsyncToolAndWakeup

本课深入 AgentScope Java 2.0.1 Harness 的 **Async Tool + Inbox + Wakeup** 运行机制。

## 1. 普通 Tool timeout 和 Async Tool 的区别

第 25 课的 timeout 更像：

```text
Tool 超过执行预算
→ 调用失败/超时
```

本课：

```text
Tool 执行超过 offload timeout
        ↓
底层 Tool 不取消，继续后台执行
        ↓
AsyncToolMiddleware 立即返回 placeholder ToolResult
        ↓
LLM 可以继续 reasoning
        ↓
后台 Tool 真正完成
        ↓
MessageBus.inboxPush(sessionId, HintBlock payload)
        ↓
MessageBus.enqueueWakeup(...)
        ↓
下一个 reasoning step 由 InboxMiddleware 注入真实结果
```

## 2. 真实 2.0.1 Builder API

```java
HarnessAgent.builder()
    .messageBus(bus)
    .asyncToolTimeout(Duration.ofMillis(60))
    .asyncToolRegistry(registry)
    ...
```

只要设置 `messageBus`，Harness 会自动挂 `InboxMiddleware`；同时设置 `asyncToolTimeout` 后再挂 `AsyncToolMiddleware`。

## 3. MessageBus 三种语义

2.0.1 的 `MessageBus` 不只是一个简单 Queue：

```text
Mode A: drain queue
  单消费者，read = ack/remove

Mode C: replay log
  多消费者，各自 cursor

Mode D: transient broadcast
  只给当前订阅者，不保存历史
```

Domain helper：

```text
inboxPush / inboxDrain
sessionPublishEvent / sessionReadEvents
enqueueWakeup / subscribeWakeup
```

## 4. 本课 slow_report

```text
slow_report
sleep 250ms
return report-ready:<topic>
```

而 AsyncTool timeout 只有 60ms，所以必然：

```text
60ms
 ↓
offload
 ↓
placeholder
 ↓
Agent 先返回

约 250ms
 ↓
真实结果完成
 ↓
inbox + wakeup
```

## 5. AsyncToolRegistry

状态：

```text
RUNNING
├── COMPLETED
├── FAILED
└── TIMEOUT
```

它的价值不是“保存结果给 UI 看”这么简单，还用于进程异常后识别 stale RUNNING 任务。

接口：

```java
register(record)
complete(id, result)
fail(id, error)
findStale(sessionId, ttl)
markTimeout(id)
```

## 6. 为什么后台 Tool 不应该被模型轮询

placeholder 中会明确告诉模型：

```text
DO NOT poll, query, or wait for the result yourself.
```

正确行为：

```text
继续其他独立任务
或先给用户一个文本回复
等待系统主动 wakeup
```

这就是 event-driven Agent，而不是：

```text
while(true) {
  tool_status()
}
```

## 7. API 实验

启动：

```bash
./mvnw -pl 47-AsyncToolAndWakeup spring-boot:run
```

触发：

```bash
curl -X POST http://localhost:18081/api/async/chat \
  -H 'Content-Type: application/json' \
  -d '{"userId":"alice","sessionId":"s1","message":"生成日报"}'
```

很快会返回类似：

```text
slow_report 已转入后台执行，我可以先继续处理其他工作。
```

稍等后查看：

```bash
curl http://localhost:18081/api/async/registry
curl http://localhost:18081/api/async/inbox/s1
curl http://localhost:18081/api/async/wakeups
```

## 8. 多 Pod 怎么办

本课 `LessonMessageBus` 是教学用内存实现。

生产多副本必须换成前面第 23/38 课学过的 distributed backend 提供的 MessageBus / AsyncToolRegistry，否则：

```text
Pod A 后台 Tool 完成
Pod B 持有下一次用户请求
```

B 看不到 A JVM 内的数据。

## 9. 与第 46 课的关系

Team mailbox / team wakeup 与 Async Tool 的思想是一致的：

```text
不要让 Agent 主动轮询世界
让外部事件进入 inbox
再主动 wakeup 对应 session
```

## 10. 自动化测试

```bash
./mvnw -pl 47-AsyncToolAndWakeup test
```

真实验证：

1. Agent 首次回复来自 async placeholder 后的 reasoning。
2. slow_report 只执行一次。
3. Registry 最终为 COMPLETED。
4. inbox 收到真实 Tool 结果。
5. wakeup queue 收到 session 唤醒请求。
