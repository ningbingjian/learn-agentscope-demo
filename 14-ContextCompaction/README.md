# 14-ContextCompaction

本节专门学习 AgentScope Harness 的 **Context Compaction（上下文压缩）**。

第 13 课解决“哪些事实值得跨 session 长期保留”；本节解决另一个问题：

> 当前 session 越聊越长，模型上下文窗口快装不下了怎么办？

## 学习目标

完成本节后，你应该能够理解：

- Compaction 与 Harness Memory 的区别。
- `CompactionConfig` 中 trigger 与 keep 两组参数的职责。
- 为什么压缩不是简单删除旧消息。
- `ConversationCompactor` 如何执行 `prefix -> summary + tail`。
- 为什么工具调用与工具结果不能被切断。
- `flushBeforeCompact` 与 `offloadBeforeCompact` 在生产中的意义。
- 模型上下文真正溢出时，Harness 为什么还能做一次 emergency compaction。

## 1. Compaction 解决什么问题

一个 session 不断追加：

```text
user-1
assistant-1
user-2
assistant-2
...
user-100
assistant-100
```

如果每一轮都完整发送给模型：

```text
输入 token 越来越大
成本越来越高
延迟越来越高
最终超过 context window
```

Compaction 的目标不是忘掉一切，而是：

```text
旧消息前缀
    ↓
LLM 总结
    ↓
1 条 summary
    +
最近若干原始消息
```

## 2. 核心算法

`ConversationCompactor` 的主流程可以理解成：

```text
conversation messages
        ↓
检查 trigger
        ↓
确定 cutoff
        ↓
可选：flush 长期记忆
        ↓
可选：offload 原始消息
        ↓
LLM summarization
        ↓
summary message + preserved tail
```

### 为什么保留 tail

摘要适合保存重要上下文，但最近几轮的精细表达最好不要马上压缩。

所以：

```text
旧历史 -> summary
最近消息 -> 原样保留
```

## 3. Trigger 与 Keep

本模块为了方便实验，使用非常低的阈值：

```java
CompactionConfig.builder()
        .triggerMessages(6)
        .triggerTokens(Integer.MAX_VALUE)
        .keepMessages(2)
        .keepTokens(0)
        .build();
```

### triggerMessages

当 conversation message 数达到阈值就触发。

### triggerTokens

生产通常更应该关注 token。Harness 默认还能根据模型 context window 动态计算阈值。

本实验把它设成极大值，只让 message count 决定是否触发。

### keepMessages

摘要后保留最近多少条 conversation message。

### keepTokens

`0` 表示使用 `keepMessages` 规则。

## 4. 本节为什么关闭 Memory Hooks

配置：

```java
.disableMemoryHooks()
.disableMemoryTools()
```

同时：

```java
.flushBeforeCompact(false)
.offloadBeforeCompact(false)
```

这样实验只剩下一条主线：

```text
message count
   ↓
summary
   ↓
state context 被替换
```

不会同时看到 daily memory、MEMORY.md、session offload 等副作用。

生产环境通常不建议照搬这些关闭项。

## 5. Summary Message

Compactor 会插入一个特殊消息：

```java
ConversationCompactor.SUMMARY_MSG_NAME
```

其值用于标识“这不是普通用户消息，而是之前历史的压缩摘要”。

所以本模块 `/api/compaction/state` 会统计：

```json
{
  "contextSize": 3,
  "compactionSummaryCount": 1,
  "messages": []
}
```

这比只看最终回复更容易理解内部发生了什么。

## 6. 为什么不能随便从中间切消息

ReAct 对话可能包含：

```text
ASSISTANT: tool_call(id=abc)
TOOL: result(id=abc)
```

如果 cutoff 恰好切在两者中间：

```text
tool_call 被摘要掉
但 tool_result 被保留
```

上下文就不完整了。

所以 `ConversationCompactor` 会调整 cutoff，避免拆开 tool-call / tool-result 关系。

## 7. 项目结构

```text
14-ContextCompaction
├── .agentscope/workspace/AGENTS.md
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/contextcompaction
    │   │   ├── ContextCompactionApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   └── web/CompactionController.java
    │   └── resources/application.yml
    └── test/java/com/example/agentscope/contextcompaction
        └── ConversationCompactorTest.java
```

## 8. 一步步编码

### 第一步：创建 CompactionConfig

```java
@Bean
CompactionConfig compactionConfig() {
    return CompactionConfig.builder()
            .triggerMessages(6)
            .triggerTokens(Integer.MAX_VALUE)
            .keepMessages(2)
            .keepTokens(0)
            .flushBeforeCompact(false)
            .offloadBeforeCompact(false)
            .prune(null)
            .build();
}
```

### 第二步：交给 HarnessAgent

```java
HarnessAgent.builder()
        .model(model)
        .workspace(Paths.get(".agentscope/workspace"))
        .compaction(compactionConfig)
        .disableMemoryHooks()
        .disableMemoryTools()
        .build();
```

### 第三步：正常 call

Controller 根本不需要手动执行 compact：

```java
agent.call(new UserMessage(message), context).block();
```

Compaction 是 Harness 生命周期的一部分。

### 第四步：观察 AgentState

```java
AgentState state = agent.getDelegate().getAgentState(userId, sessionId);
```

然后检查：

```java
ConversationCompactor.SUMMARY_MSG_NAME.equals(msg.getName())
```

## 9. 启动

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 14-ContextCompaction spring-boot:run
```

## 10. 实验

连续发送多轮同 session 请求：

```bash
curl -X POST http://localhost:18081/api/compaction/chat \
  -H 'Content-Type: application/json' \
  -d '{"userId":"alice","sessionId":"compact-demo","message":"第1轮：记住项目代号是 Orion。"}'
```

再依次发送第 2、3、4 轮。

每轮后查看：

```bash
curl 'http://localhost:18081/api/compaction/state?userId=alice&sessionId=compact-demo'
```

触发前你会看到 context 持续增长。

触发后会出现：

```text
__compaction_summary__
```

并且 context 数量明显下降。

## 11. 查看配置

```bash
curl http://localhost:18081/api/compaction/config
```

本模块应该返回：

```json
{
  "triggerMessages": 6,
  "triggerTokens": 2147483647,
  "keepMessages": 2,
  "keepTokens": 0,
  "flushBeforeCompact": false,
  "offloadBeforeCompact": false
}
```

## 12. 自动化测试为什么更重要

真实 LLM 回复消息数量和 token 都有波动，因此本模块另外直接测试 `ConversationCompactor`。

FakeModel 永远返回：

```text
condensed summary
```

输入 6 条消息：

```text
turn-1 user
turn-1 assistant
turn-2 user
turn-2 assistant
turn-3 user
turn-3 assistant
```

配置：

```text
trigger = 4
keep = 2
```

结果必须是：

```text
summary
turn-3 user
turn-3 assistant
```

运行：

```bash
./mvnw -pl 14-ContextCompaction test
```

## 13. 生产默认值与实验值不同

本模块阈值故意极小，只为教学。

生产默认行为更接近：

```text
动态根据模型 context window 算 token trigger
保留一段动态 tail token budget
同时支持 tool result pruning
必要时做 memory flush 与 offload
```

不要把 `triggerMessages(6)` 直接复制到生产。

## 14. Emergency Compaction

除了阈值主动触发，如果模型真的报：

```text
context_length_exceeded
```

只要 Harness 配置了 compaction，它还可以强制做一轮紧急压缩后重试。

这属于“主动治理 + 最后一层兜底”。

## 15. Compaction 与 Memory 的关系

```text
Harness Memory
目的：跨会话长期沉淀事实
文件：memory/*.md + MEMORY.md

Context Compaction
目的：缩短当前 session 模型上下文
结果：summary + recent tail
```

二者会协作，但不是同一个能力。

## 16. 本节边界

本节不展开：

- Memory consolidation；
- tool-result eviction；
- RAG；
- 分布式 session offload；
- 自定义 summary 模型成本优化。

下一课进入 application-layer RAG。
