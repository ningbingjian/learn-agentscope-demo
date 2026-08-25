# 05-MultiUserConcurrency

本节学习一个 `HarnessAgent` 单例如何安全地同时服务多个用户和会话：

```text
相同 (userId, sessionId) -> 按请求顺序串行执行
不同 (userId, sessionId) -> 可以并行执行
```

第 02 节关注状态的保存、恢复与隔离；本节只关注多个请求同时到达时的并发调度。

## 学习目标

完成本节后，你应该能够理解：

- 为什么 Spring 中不需要为每个用户创建一个 `HarnessAgent` Bean。
- AgentScope 如何用 `(userId, sessionId)` 生成会话槽位。
- 为什么同一会话的两个请求不能同时修改 `AgentState`。
- 同会话串行与不同会话并行是如何同时实现的。
- 单 JVM 内的会话串行与分布式部署有什么区别。

## 理论基础

### 单例 Agent 可以服务多个会话

`HarnessAgent` 中相对固定的部分包括名称、系统提示词、模型、工具和 Workspace 配置。真正会变化的对话状态按 `(userId, sessionId)` 存放在不同槽位中：

```text
Spring 容器
└── HarnessAgent 单例
    ├── alice/session-1 -> AgentState A
    ├── alice/session-2 -> AgentState B
    └── bob/session-1   -> AgentState C
```

所以，多用户服务的关键不是创建许多 Agent 实例，而是为每次调用传入正确的 `RuntimeContext`。

### 为什么同一会话必须串行

假设同一会话的 A、B 两个请求同时读取旧状态：

```text
旧状态 S0
├── 请求 A 读取 S0 -> 生成 S1
└── 请求 B 读取 S0 -> 生成 S2
```

如果二者并行保存，后写入的状态可能覆盖先写入的状态，导致丢消息、上下文顺序错误或工具状态不一致。

AgentScope 会把同一槽位的调用排成队列：

```text
alice/session-1: 请求 A ─────> 请求 B
alice/session-2: 请求 C ─────>
```

请求 B 等 A 完整结束后才进入 Agent 生命周期；请求 C 属于不同槽位，可以与 A 并行。

### 串行键是什么

可以把内部调度键理解为：

```text
slotKey = userId + "/" + sessionId
```

因此下面三种情况的结果不同：

| 第一个请求 | 第二个请求 | 调度结果 |
| --- | --- | --- |
| `alice/s1` | `alice/s1` | 相同槽位，串行 |
| `alice/s1` | `alice/s2` | 不同会话，并行 |
| `alice/s1` | `bob/s1` | 不同用户，并行 |

`sessionId` 只要求在同一个用户下唯一；两个用户使用相同的 `sessionId` 不会变成同一会话。

### 自动串行化的作用范围

本节验证的是同一个 Agent 实例中的调度。串行队列保存在当前 JVM 的 Agent 实例内，因此：

- 单实例 Spring Boot 服务中，同一会话会自动串行。
- 多实例部署时，共享 Redis 或数据库可以共享状态，但本地队列不会自动变成跨节点锁。
- 生产集群还需要结合会话粘滞、分布式协调或 AgentScope 的生产运行后端设计请求路由。

换句话说，`AgentStateStore` 解决“状态存在哪里”，会话调度解决“谁先修改状态”，二者不是同一个问题。

## 本节如何观察并发

真实模型调用时间不固定，不方便直接比较。模块注册了一个仅用于实验的 `pause` 工具：

```text
HTTP 请求
   ↓
RuntimeContext(userId, sessionId)
   ↓
AgentScope 按会话槽位调度
   ↓
模型调用 pause 工具等待 N 秒
   ↓
返回 elapsedMillis
```

同时发送两个等待 3 秒的请求：

- 相同会话：第二个请求需要排队，总耗时接近两次调用之和。
- 不同会话：两个调用可以重叠，总耗时接近较慢的那一次。

模型推理本身也需要时间，所以实际结果不会正好是 6 秒或 3 秒。

## 项目结构

```text
05-MultiUserConcurrency
├── .agentscope/workspace/AGENTS.md
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/multiuserconcurrency
    │   │   ├── MultiUserConcurrencyApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   ├── tool/DelayTools.java
    │   │   └── web/ConcurrencyController.java
    │   └── resources/application.yml
    └── test/java/com/example/agentscope/multiuserconcurrency
        └── SessionConcurrencyTest.java
```

## 核心代码

### 1. Agent 仍然是单例 Bean

```java
@Bean(destroyMethod = "close")
HarnessAgent concurrencyAgent(Model model, DelayTools delayTools) {
    Toolkit toolkit = new Toolkit();
    toolkit.registerTool(delayTools);

    return HarnessAgent.builder()
            .name("concurrency-agent")
            .model(model)
            .toolkit(toolkit)
            .workspace(Paths.get(".agentscope/workspace"))
            .build();
}
```

Spring 默认只创建一个 `HarnessAgent`，所有 HTTP 请求共享它。AgentScope 根据每次调用的上下文选择状态槽位，而不是把所有用户放进同一份对话状态。

### 2. 每个请求创建独立 RuntimeContext

```java
RuntimeContext context = RuntimeContext.builder()
        .userId(request.userId())
        .sessionId(request.sessionId())
        .build();

Msg reply = agent.call(new UserMessage(prompt), context).block();
```

Controller 本身不加 `synchronized`，也不维护会话锁。`HarnessAgent` 内部包装的 Agent 会使用这两个 ID 作为串行键。

### 3. pause 工具只是实验计时器

```java
@Tool(name = "pause", readOnly = true, concurrencySafe = true)
public String pause(int seconds) {
    Thread.sleep(Duration.ofSeconds(seconds).toMillis());
    return "waited " + seconds + " second(s)";
}
```

它不属于本节要学习的业务能力，只用于让请求保持运行一段可观察的时间。参数限制为 1～10 秒。

## 自动化测试原理

`SessionConcurrencyTest` 不调用 DashScope，而是使用一个延迟 150 毫秒的假模型，并在 Agent 的 `PreCallEvent` / `PostCallEvent` 上记录主调用生命周期的最大并发数：

```text
相同 alice/session-1 + alice/session-1 -> maxConcurrentCalls = 1
不同 alice/session-1 + alice/session-2 -> maxConcurrentCalls = 2
不同 alice/shared   + bob/shared       -> maxConcurrentCalls = 2
```

这样可以稳定验证 AgentScope 的调度行为，也不会因为网络或模型回复变化导致测试不可靠。

这里特意统计 Agent 调用生命周期，而不是底层模型调用次数，因为 `HarnessAgent` 的记忆维护等内部能力也可能使用模型，两者不能简单画等号。

单独运行本模块测试：

```bash
./mvnw -pl 05-MultiUserConcurrency test
```

## 启动

在项目根目录执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -pl 05-MultiUserConcurrency spring-boot:run
```

服务端口是 `18081`。测试 Key 保存在本机不会提交到 Git 的 `application-local.yml`；首次克隆时可以复制同目录的 `application-local.example.yml` 后填写。

## 测试一：相同会话串行

同时发送两个 `alice/same-session` 请求：

```bash
time (
  curl -s -X POST http://localhost:18081/api/concurrency \
    -H 'Content-Type: application/json' \
    -d '{"userId":"alice","sessionId":"same-session","delaySeconds":3}' \
    > /tmp/agentscope-same-1.json &

  curl -s -X POST http://localhost:18081/api/concurrency \
    -H 'Content-Type: application/json' \
    -d '{"userId":"alice","sessionId":"same-session","delaySeconds":3}' \
    > /tmp/agentscope-same-2.json &

  wait
)
```

服务日志中两个请求会先后进入 `pause` 工具。第二个请求的 `elapsedMillis` 还会包含排队时间。

查看返回结果：

```bash
cat /tmp/agentscope-same-1.json
cat /tmp/agentscope-same-2.json
```

## 测试二：不同会话并行

保持用户相同，但改用两个不同的 `sessionId`：

```bash
time (
  curl -s -X POST http://localhost:18081/api/concurrency \
    -H 'Content-Type: application/json' \
    -d '{"userId":"alice","sessionId":"session-a","delaySeconds":3}' \
    > /tmp/agentscope-parallel-1.json &

  curl -s -X POST http://localhost:18081/api/concurrency \
    -H 'Content-Type: application/json' \
    -d '{"userId":"alice","sessionId":"session-b","delaySeconds":3}' \
    > /tmp/agentscope-parallel-2.json &

  wait
)
```

日志中两个 `pause tool started` 应在两个完成日志之前出现，说明它们发生了重叠。

也可以把第二个请求改成 `bob/session-a`，用来证明相同 `sessionId`、不同 `userId` 仍属于不同槽位。

## 响应字段

接口会返回类似内容：

```json
{
  "requestId": "c535f894-efbd-4af4-879e-0d17c3fca04f",
  "userId": "alice",
  "sessionId": "session-a",
  "reply": "等待完成。",
  "acceptedAt": "2026-08-25T01:00:00Z",
  "completedAt": "2026-08-25T01:00:04Z",
  "elapsedMillis": 4120
}
```

`elapsedMillis` 从 Controller 接收请求开始计时，因此包含同会话排队、模型推理、工具执行和最终回复生成的全部时间。

## 本节边界

本节只学习 Agent 单例、会话槽位和并发调度。`pause` 工具只是观测手段；不继续展开工具定义、流式响应、分布式锁或压测框架。

## 延伸阅读

- [AgentScope Java：快速开始中的多用户并发](https://java.agentscope.io/v2/zh/docs/quickstart.html)
- [AgentScope Java：Agent](https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html)
- [AgentScope Java：上线指南](https://java.agentscope.io/v2/zh/docs/others/going-to-production.html)
