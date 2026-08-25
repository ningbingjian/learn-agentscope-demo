# 06-AgentInterrupt

本节学习 AgentScope Java 2.x 的 **Agent 中断机制**：当一个会话正在执行时，如何从另一个 HTTP 请求中只中断指定的 `(userId, sessionId)`，而不影响同一个 Agent 上的其他会话。

```text
alice/session-1 正在执行 ──> interrupt(alice, session-1) ──> INTERRUPTED
bob/session-1   正在执行 ────────────────────────────────> 正常继续
```

第 05 节学习“同会话串行、不同会话并行”；本节继续回答另一个生产场景问题：

> 多个会话都在运行时，用户点击“停止生成”，怎样只停止自己的那个会话？

## 学习目标

完成本节后，你应该能够理解：

- `interrupt` 为什么必须带明确的会话身份。
- `RuntimeContext(userId, sessionId)` 如何定位要中断的会话。
- 为什么中断一个 session 不会关闭 Spring 中的 `HarnessAgent` Bean。
- 如何通过 `GenerateReason.INTERRUPTED` 判断一次调用是被中断结束的。
- 为什么 AgentScope 的中断属于“协作式中断”，而不是直接执行 Java 的 `Thread.interrupt()`。
- AgentScope Java `2.0.1` 中 `HarnessAgent` 与 `ReActAgent` 的 per-session interrupt API 差异。

## 理论基础

### 中断的是一次会话调用，不是整个 Agent

当前模块仍然只创建一个 Spring 单例 `HarnessAgent`：

```text
Spring 容器
└── HarnessAgent 单例
    ├── alice/session-1 -> 正在运行
    ├── alice/session-2 -> 正在运行
    └── bob/session-1   -> 正在运行
```

如果用户 Alice 只想取消 `session-1`，目标应该是：

```text
(userId = alice, sessionId = session-1)
```

而不是关闭 Agent：

```text
HarnessAgent.close()   X
Spring Bean 销毁        X
线程池 shutdown         X
```

中断信号属于某一个会话槽位，其他 session 可以继续执行。

### RuntimeContext 决定目标会话

正常调用时：

```java
RuntimeContext context = RuntimeContext.builder()
        .userId("alice")
        .sessionId("session-1")
        .build();

agent.call(new UserMessage("开始任务"), context).block();
```

中断时同样构造目标会话：

```java
RuntimeContext target = RuntimeContext.builder()
        .userId("alice")
        .sessionId("session-1")
        .build();
```

因此可以把一次中断理解为：

```text
找到 alice/session-1 对应的 AgentState
        ↓
找到该状态里的 InterruptControl
        ↓
设置 interrupt signal
        ↓
正在执行的 Agent 在后续检查点发现信号
        ↓
走中断处理流程
```

### AgentScope Java 2.0.1 的一个重要 API 差异

根项目当前锁定：

```xml
<agentscope.version>2.0.1</agentscope.version>
```

`ReActAgent` 在这个版本已经提供 per-session 中断：

```java
reactAgent.interrupt(target);
reactAgent.interrupt("alice", "session-1");
```

但是 `HarnessAgent` 在 `2.0.1` 中还没有直接转发这些带 `RuntimeContext` / `(userId, sessionId)` 的重载，只直接暴露旧的：

```java
agent.interrupt();
agent.interrupt(msg);
```

这两个旧方法使用 Agent 的默认 session，不适合本项目这种多用户、多会话单例 Agent 场景。

因此本节按 **项目真实依赖版本** 使用：

```java
agent.getDelegate().interrupt(target);
```

也就是让 `HarnessAgent` 内部包装的 `ReActAgent` 精准中断目标会话。

> 后续如果升级 AgentScope，请先检查当前版本的 `HarnessAgent` 是否已经直接提供 per-session interrupt API，再决定是否移除 `getDelegate()` 这一层。

### interrupt 不是 Thread.interrupt

这是本节非常重要的一个边界。

AgentScope 的中断是框架级的协作式信号：

```text
interrupt signal
      ↓
AgentScope 在执行检查点发现信号
      ↓
停止后续 reasoning / acting
      ↓
返回 INTERRUPTED
```

典型检查点包括推理开始、工具执行阶段切换以及模型流式输出等位置。

本模块故意继续使用一个阻塞式 `pause` 工具：

```java
Thread.sleep(...);
```

当 Agent 已经进入这个 Java 方法后，调用 AgentScope 的 `interrupt` **不会直接对这个线程执行 `Thread.interrupt()`**。

因此可能看到：

```text
pause tool started
       ↓
收到 /cancel
       ↓
interrupt signal 已设置
       ↓
pause 仍继续执行到返回
       ↓
Agent 回到下一个框架检查点
       ↓
发现 interrupt
       ↓
GenerateReason.INTERRUPTED
```

这不是中断失效，而是协作式中断的工作方式。

生产系统中的长耗时 Tool 如果要求“点击取消后立刻停止”，通常还需要让 Tool 自身支持取消、超时或异步任务取消机制；本节暂不展开。

## 一次完整中断流程

```text
终端 A
POST /api/interrupt/run
        ↓
RuntimeContext(alice, session-1)
        ↓
HarnessAgent.call(...)
        ↓
模型决定调用 pause
        ↓
pause 正在执行

终端 B
POST /api/interrupt/cancel
        ↓
RuntimeContext(alice, session-1)
        ↓
HarnessAgent.getDelegate()
        ↓
ReActAgent.interrupt(target)
        ↓
Alice session-1 的 InterruptControl = interrupted
        ↓
Agent 在后续检查点处理信号
        ↓
返回 GenerateReason.INTERRUPTED
```

## 项目结构

```text
06-AgentInterrupt
├── .agentscope/workspace/AGENTS.md
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/agentinterrupt
    │   │   ├── AgentInterruptApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   ├── tool/DelayTools.java
    │   │   └── web/InterruptController.java
    │   └── resources/application.yml
    └── test/java/com/example/agentscope/agentinterrupt
        └── SessionInterruptTest.java
```

## 核心代码

### 1. DelayTools 使用 @Component

本节把工具类写成独立 Spring 组件：

```java
@Component
public class DelayTools {

    @Tool(name = "pause", readOnly = true, concurrencySafe = true)
    public String pause(int seconds) {
        Thread.sleep(Duration.ofSeconds(seconds).toMillis());
        return "waited " + seconds + " second(s)";
    }
}
```

因此不再需要：

```java
@Bean
DelayTools delayTools() {
    return new DelayTools();
}
```

Spring 会扫描 `@Component` 并把 `DelayTools` 注入 `AgentConfiguration`。

### 2. AgentConfiguration 只负责组装 Agent

```java
@Bean(destroyMethod = "close")
HarnessAgent interruptAgent(Model model, DelayTools delayTools) {
    Toolkit toolkit = new Toolkit();
    toolkit.registerTool(delayTools);

    return HarnessAgent.builder()
            .name("interrupt-agent")
            .model(model)
            .toolkit(toolkit)
            .workspace(Paths.get(".agentscope/workspace"))
            .build();
}
```

这里仍然是一个单例 Agent。

### 3. 启动一个可观察的耗时调用

接口：

```text
POST /api/interrupt/run
```

Controller 为本次请求创建：

```java
RuntimeContext context = RuntimeContext.builder()
        .userId(request.userId())
        .sessionId(request.sessionId())
        .build();
```

然后调用：

```java
Msg reply = agent.call(new UserMessage(prompt), context).block();
```

最终响应额外返回：

```java
reply.getGenerateReason().name()
```

正常结束通常是：

```text
MODEL_STOP
```

被 AgentScope 中断后则应该观察到：

```text
INTERRUPTED
```

### 4. 精准中断目标 session

接口：

```text
POST /api/interrupt/cancel
```

核心代码只有：

```java
RuntimeContext target = RuntimeContext.builder()
        .userId(request.userId())
        .sessionId(request.sessionId())
        .build();

agent.getDelegate().interrupt(target);
```

最重要的是：取消请求必须使用与运行请求一致的 `userId + sessionId`。

## 启动

在项目根目录执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 06-AgentInterrupt spring-boot:run
```

服务端口：

```text
18081
```

## 测试一：中断单个会话

先在终端 A 启动一个 10 秒任务：

```bash
curl -X POST http://localhost:18081/api/interrupt/run \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"session-1",
    "delaySeconds":10
  }'
```

观察服务日志，等出现：

```text
pause tool started: seconds=10
```

再在终端 B 中执行：

```bash
curl -X POST http://localhost:18081/api/interrupt/cancel \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"session-1"
  }'
```

取消接口会很快返回：

```json
{
  "userId": "alice",
  "sessionId": "session-1",
  "status": "INTERRUPT_SIGNAL_SENT",
  "signaledAt": "2026-08-25T08:00:00Z"
}
```

因为本示例的 `pause` 是阻塞 Java Tool，原来的 `/run` 请求不一定立刻结束；等它进入下一个 AgentScope 检查点后，响应中的重点字段应该是：

```json
{
  "generateReason": "INTERRUPTED"
}
```

### 为什么 cancel 返回了，run 还可能继续几秒

因为两件事不同：

```text
/cancel 返回
    = 中断信号已经成功写入目标会话

/run 返回
    = 正在运行的 Agent 已经走到检查点并处理了中断
```

不要把 `INTERRUPT_SIGNAL_SENT` 理解成“目标 Java 线程已经被立即杀死”。

## 测试二：证明其他 session 不受影响

终端 A：

```bash
curl -X POST http://localhost:18081/api/interrupt/run \
  -H 'Content-Type: application/json' \
  -d '{"userId":"alice","sessionId":"session-a","delaySeconds":10}'
```

终端 B：

```bash
curl -X POST http://localhost:18081/api/interrupt/run \
  -H 'Content-Type: application/json' \
  -d '{"userId":"bob","sessionId":"session-b","delaySeconds":10}'
```

终端 C 只取消 Alice：

```bash
curl -X POST http://localhost:18081/api/interrupt/cancel \
  -H 'Content-Type: application/json' \
  -d '{"userId":"alice","sessionId":"session-a"}'
```

预期：

```text
alice/session-a -> INTERRUPTED
bob/session-b   -> 正常完成
```

这就是 per-session interrupt 的核心价值。

## 自动化测试

本模块的测试不调用 DashScope，而是直接验证 `ReActAgent` 内部为每个会话维护的 `InterruptControl`：

```text
alice/session-1 -> interrupt = true
bob/session-1   -> interrupt = false
```

还会验证带消息中断时，中断消息只保存在目标 session 的 interrupt signal 中。

运行：

```bash
./mvnw -pl 06-AgentInterrupt test
```

## 带消息中断

除了：

```java
agent.getDelegate().interrupt(target);
```

`ReActAgent` 还支持：

```java
agent.getDelegate().interrupt(
        target,
        new UserMessage("用户主动取消了当前任务。")
);
```

这个消息会和中断上下文绑定，可用于后续恢复时告诉 Agent 用户为什么停止任务。

本节 Controller 先使用最简单的无消息中断，避免一次引入太多概念。

## 不应该怎么写

### 不要在多会话场景直接用无参数 interrupt

```java
agent.interrupt();
```

在当前 `2.0.1` 的 `HarnessAgent` 中，这种方式使用默认 session，无法表达“我要取消 alice/session-1”。

### 不要自己在 Controller 保存一个全局 cancelFlag

例如：

```java
AtomicBoolean cancelled;
```

一个全局 flag 会把所有用户混到一起，也绕过了 AgentScope 已经提供的 per-session 状态机制。

### 不要通过销毁 Agent Bean 实现用户取消

用户取消一个请求并不意味着：

```text
关闭模型
关闭 HarnessAgent
停止整个 Spring Boot
影响其他用户
```

正确粒度是 session。

## 本节边界

本节只学习：

```text
外部请求
   ↓
定位 userId/sessionId
   ↓
发送 per-session interrupt signal
   ↓
Agent 在检查点处理
   ↓
GenerateReason.INTERRUPTED
```

暂不继续展开：

- Tool 自身的立即取消机制
- Reactor subscription cancel
- HTTP 客户端断连自动取消
- 超时控制
- Graceful Shutdown
- Human-in-the-loop
- 分布式多节点 interrupt 路由

这些能力后面单独学习会更清晰。

## 延伸阅读

- [AgentScope Java：Agent / Interrupt](https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html)
- [AgentScope Java：RuntimeContext 与 AgentState](https://java.agentscope.io/v2/zh/docs/building-blocks/context.html)
