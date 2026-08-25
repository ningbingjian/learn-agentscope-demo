# 11-AgentObserve

本节学习 `Agent.observe(...)`：**把一条消息放进另一个 Agent 的上下文，但不触发它推理，也不生成回复。**

这是从“单 Agent”走向“多 Agent 协作”之前非常重要的基础能力。

```text
ResearcherAgent.call()
        |
        v
研究结果 Msg
        |
        | writerAgent.observe(msg)
        v
WriterAgent context 增加研究结果
        |
        | 此时 Writer 不调用 LLM、不回复
        v
之后 writerAgent.call(...)
        |
        v
Writer 利用之前观察到的研究结果写文章
```

## 学习目标

完成本节后，你应该能够理解：

- `call()` 与 `observe()` 的本质区别。
- 为什么 `observe()` 返回 `Mono<Void>`。
- `observe()` 为什么适合 Agent 间共享上下文。
- 被 observe 的 `Msg` 会进入目标 Agent 的 context。
- `observe()` 本身不会调用模型。
- AgentScope Java 2.0.1 中 `observe()` 没有 `RuntimeContext` 重载意味着什么。
- 为什么这还不是完整的 SubAgent 编排。

## 一、call 和 observe 的区别

### call

```java
Msg reply = agent.call(new UserMessage("帮我总结")).block();
```

流程：

```text
输入消息
  -> 写入上下文
  -> Agent 推理
  -> LLM / Tool
  -> 返回 Msg
```

### observe

```java
agent.observe(otherAgentMessage).block();
```

流程：

```text
输入消息
  -> 写入上下文
  -> 完成
```

没有：

```text
LLM 推理
Tool Calling
Agent reply
```

因此它的返回类型是：

```java
Mono<Void>
```

而不是：

```java
Mono<Msg>
```

## 二、本节案例：Researcher -> Writer

本节创建两个 Agent：

```text
researcher-agent
    负责研究主题

writer-agent
    负责使用已有研究资料写作
```

工作流分成两次 HTTP 操作。

### 第一次：研究

```text
POST /api/observe/research
        |
        v
Researcher.call(topic)
        |
        v
research Msg
        |
        v
Writer.observe(research Msg)
```

接口返回研究内容，但 Writer 此时不会主动生成任何文字。

### 第二次：写作

```text
POST /api/observe/write
        |
        v
Writer.call("根据已有研究写摘要")
        |
        v
Writer 的上下文里已经有 Researcher 的 Msg
        |
        v
生成文章
```

这正是 observe 的价值：

> 让另一个 Agent“知道某件事”，但不要求它现在立刻行动。

## 三、一步步把案例编码出来

### 第 1 步：创建 ResearcherAgent

```java
@Bean(name = "researcherAgent", destroyMethod = "close")
ReActAgent researcherAgent(Model model) {
    return ReActAgent.builder()
            .name("researcher-agent")
            .sysPrompt("你是研究助手...")
            .model(model)
            .build();
}
```

### 第 2 步：创建 WriterAgent

```java
@Bean(name = "writerAgent", destroyMethod = "close")
ReActAgent writerAgent(Model model) {
    return ReActAgent.builder()
            .name("writer-agent")
            .defaultSessionId("writer-default")
            .sysPrompt("写作时优先利用上下文里其他 Agent 的研究结果...")
            .model(model)
            .build();
}
```

这里显式设置 `defaultSessionId`，是因为本节要观察 AgentScope Java 2.0.1 的 `observe()` 行为。

### 第 3 步：Researcher 正常 call

```java
Msg researchReply = researcherAgent
        .call(new UserMessage("请研究 Agent 中间件"))
        .block();
```

Researcher 会真的调用模型。

### 第 4 步：把 Researcher 的 Msg 交给 Writer observe

核心只有一行：

```java
writerAgent.observe(researchReply).block();
```

注意，我们没有做：

```java
writerAgent.call(researchReply)
```

因为那样会立即让 Writer 开始推理。

### 第 5 步：观察 Writer 的 context

本案例提供：

```text
GET /api/observe/writer-context
```

用于直接查看 Writer 当前已经看见哪些消息。

Research 之后，你应该能看到类似：

```json
[
  {
    "role": "ASSISTANT",
    "name": "researcher-agent",
    "text": "...研究结果..."
  }
]
```

这说明消息确实进入 Writer 上下文。

### 第 6 步：再让 Writer call

```java
Msg reply = writerAgent
        .call(new UserMessage("根据已有研究结果写一段摘要"))
        .block();
```

这次才发生真正推理。

## 四、AgentScope Java 2.0.1 的一个重要版本细节

当前项目锁定：

```xml
<agentscope.version>2.0.1</agentscope.version>
```

在这个版本中 `ObservableAgent` 提供：

```java
Mono<Void> observe(Msg msg);
Mono<Void> observe(List<Msg> msgs);
```

但没有：

```java
observe(msg, RuntimeContext)
```

`ReActAgent#doObserve` 会把消息加入 `getAgentState()` 对应的默认状态槽位。

因此本节**故意使用单一默认 Writer session** 来把 observe 的核心语义讲清楚。

这和前面大量使用的：

```java
agent.call(msg, runtimeContext)
```

不是同一种多会话 API。

不要在这一课里强行把 observe 包装成多租户生产方案。

## 五、启动

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"

./mvnw -pl 11-AgentObserve spring-boot:run
```

## 六、实验一：让 Researcher 研究

```bash
curl -X POST http://localhost:18081/api/observe/research \
  -H 'Content-Type: application/json' \
  -d '{
    "topic":"为什么 Agent 应该使用 Middleware 做统一可观测性"
  }'
```

响应中包含：

```text
research
writerContextMessageCount
```

重点观察后一个字段：Writer context 已经增加消息。

## 七、实验二：直接查看 Writer context

```bash
curl http://localhost:18081/api/observe/writer-context
```

此时你会发现 Researcher 的输出已经存在，但 Writer 并没有额外回复。

## 八、实验三：让 Writer 使用观察结果

```bash
curl -X POST http://localhost:18081/api/observe/write \
  -H 'Content-Type: application/json' \
  -d '{
    "instruction":"根据你已经看到的研究结果，写一个 150 字以内的中文技术摘要。"
  }'
```

这里没有把 Researcher 的研究结果重新放进请求 body。

Writer 能利用它，是因为之前：

```java
writerAgent.observe(researchReply)
```

已经把消息放入 Writer context。

## 九、为什么不是直接把字符串拼进 Prompt

当然可以写：

```java
String prompt = research + "\n请根据以上内容写文章";
```

但 observe 表达的是更明确的 Agent 语义：

```text
这是一条另一个 Agent 已经产生的 Msg
         |
         v
把它作为上下文事件交给目标 Agent
```

消息的：

```text
role
name
content
metadata
```

都可以继续保留，而不是提前压平成一个字符串。

这为后面的多 Agent 通信模型打基础。

## 十、自动化测试

`AgentObserveTest` 使用一个 `CountingModel`。

测试逻辑：

```text
callCount = 0
     |
writer.observe(researchMsg)
     |
     +-- context size = 1
     +-- callCount 仍然 = 0
```

这直接证明：

> observe 修改上下文，但不会调用 Model。

运行：

```bash
./mvnw -pl 11-AgentObserve test
```

## 十一、observe 适合哪些场景

典型场景：

### Agent 间共享结果

```text
ResearchAgent -> WriterAgent.observe(result)
```

### 旁路观察

```text
Planner output -> Auditor.observe(plan)
```

### 环境事件注入

```text
external event -> Agent.observe(eventMsg)
```

核心都是：

> 让 Agent 知道，但暂时不要它回答。

## 十二、observe 不等于 Multi-Agent Framework

本节只有：

```text
Agent A
  |
  | Msg
  v
Agent B.observe()
```

没有：

- Agent 自动选择其他 Agent。
- Task 分发。
- SubAgent 生命周期。
- 并行子任务。
- 后台任务。
- Agent Gateway。

这些属于后续 SubAgent / Harness 编排课程。

## 十三、本节边界

本节只学习一个核心知识点：

```text
observe = 接收消息，不触发回复
```

下一节会进入 Harness Workspace，先建立后续 Memory、Skills、SubAgent、Plan Mode 共同依赖的文件工作区基础。
