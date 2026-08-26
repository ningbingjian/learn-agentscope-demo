# 26-GracefulShutdownAndRecovery：优雅下线、Drain 与状态恢复

## 1. 生产中的真实问题

Kubernetes 更新 Pod 时不会问 Agent：

```text
“你现在是不是正在思考？”
```

而是最终给进程发送 SIGTERM。

如果直接杀：

```text
用户请求
  ↓
Agent reasoning
  ↓
Tool 执行一半
  ↓
SIGTERM
  ↓
进程没了
```

可能导致：

- 回复被截断；
- Tool 状态不完整；
- session 状态没持久化；
- 用户 retry 后重复执行同一动作；
- 新请求还在继续进入即将下线的 Pod。

所以生产下线应该是：

```text
RUNNING
  ↓ drain
SHUTTING_DOWN
  ├── 拒绝新请求
  ├── 等待在途请求
  ├── 到安全点结束
  └── 必要时保存状态
  ↓
TERMINATED
```

---

## 2. AgentScope 2.0.1 已经提供什么

核心组件：

```text
GracefulShutdownManager
GracefulShutdownMiddleware
AgentScopeJvmShutdownHook
GracefulShutdownConfig
PartialReasoningPolicy
```

每个 `ReActAgent` 内部都会带 `GracefulShutdownMiddleware`。

`GracefulShutdownManager` 是全局生命周期管理器。

---

## 3. 本节目标

学完应能解释：

- Drain 和 Shutdown 为什么不是一回事；
- `RUNNING / SHUTTING_DOWN / TERMINATED`；
- 为什么 drain 后必须先停止接新请求；
- active request 是怎么被追踪的；
- shutdown timeout 到达后发生什么；
- `PartialReasoningPolicy.SAVE / DISCARD`；
- 为什么生产必须配置 `AgentStateStore`；
- JVM SIGTERM hook 做什么；
- AgentScope Admin Starter 如何提供运维端点。

---

## 4. 配置 GracefulShutdownManager

本案例：

```java
manager.setConfig(new GracefulShutdownConfig(
    Duration.ofSeconds(20),
    PartialReasoningPolicy.SAVE
));
```

表示最多给在途请求 20 秒完成。

### SAVE

如果 shutdown 打断了尚未完整结束的 reasoning，尽可能保留部分状态。

### DISCARD

不保留未完成 reasoning。

一般教学和恢复场景先使用 `SAVE`。

---

## 5. 为什么还需要 AgentStateStore

Graceful Shutdown 不等于持久化。

需要：

```text
GracefulShutdownManager
       +
AgentStateStore
```

本案例使用：

```java
new JsonFileAgentStateStore(Paths.get(".agentscope/state"))
```

单机可以观察状态文件。

多 Pod 生产环境则应该换成前一课学习的 Redis / MySQL 等共享 Store。

---

## 6. 一步步编码

### Step 1：配置 state store

```java
@Bean
JsonFileAgentStateStore shutdownStateStore() {
    return new JsonFileAgentStateStore(Paths.get(".agentscope/state"));
}
```

### Step 2：设置 shutdown policy

```java
@Bean
GracefulShutdownManager gracefulShutdownManager() {
    GracefulShutdownManager manager = GracefulShutdownManager.getInstance();
    manager.setConfig(new GracefulShutdownConfig(
            Duration.ofSeconds(20),
            PartialReasoningPolicy.SAVE));
    return manager;
}
```

### Step 3：正常创建 Agent

```java
ReActAgent.builder()
    .stateStore(shutdownStateStore)
    .build();
```

ReActAgent 构造时会把状态保存器绑定到全局 Shutdown Manager。

### Step 4：加入 AgentScope Admin Starter

依赖：

```xml
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-admin-spring-boot-starter</artifactId>
</dependency>
```

开启：

```yaml
agentscope:
  admin:
    enabled: true
    write-enabled: true
```

### Step 5：暴露 drain

```text
GET  /actuator/agentscope-drain
POST /actuator/agentscope-drain
```

Drain 的意义是：

```text
停止接受 Agent 新请求
但 JVM 还活着
```

这非常适合从负载均衡池里先摘掉节点。

---

## 7. 启动

```bash
export DASHSCOPE_API_KEY="你的 Key"
export AGENTSCOPE_ADMIN_TOKEN="demo-token"
./mvnw -pl 26-GracefulShutdownAndRecovery spring-boot:run
```

状态：

```bash
curl http://localhost:18081/api/shutdown-demo/status
```

初始应为：

```text
RUNNING
acceptingRequests=true
```

---

## 8. 实验一：Drain 后拒绝新请求

先正常聊：

```bash
curl -X POST http://localhost:18081/api/shutdown-demo/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"shutdown-1",
    "message":"你好"
  }'
```

然后 Drain：

```bash
curl -X POST \
  'http://localhost:18081/actuator/agentscope-drain?confirm=yes&token=demo-token'
```

再看：

```bash
curl http://localhost:18081/api/shutdown-demo/status
```

应该看到：

```text
acceptingRequests=false
```

再发新的 Agent 请求应被拒绝。

注意：这是进程级全局状态，实验完成后需要重启应用才能回到 RUNNING。

---

## 9. 实验二：带在途请求 Drain

终端 A：

```bash
curl -X POST http://localhost:18081/api/shutdown-demo/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"slow-session",
    "message":"请调用 pause 等待 10 秒，然后回复完成。"
  }'
```

请求进行中，终端 B：

```bash
curl -X POST \
  'http://localhost:18081/actuator/agentscope-drain?confirm=yes&token=demo-token'
```

此时观察：

```text
state = SHUTTING_DOWN
activeRequests >= 1
```

Drain 不等于粗暴 kill。

---

## 10. shutdown timeout

本节设置 20 秒。

如果在途请求一直不退出：

```text
20 秒到达
   ↓
Manager 发出 shutdown timeout signal
   ↓
保存状态
   ↓
对 active request 发 SYSTEM interrupt
```

Agent 在框架安全点观察到 shutdown interrupt 后结束。

与第 06 课相同：

```text
AgentScope interrupt
!=
Thread.interrupt()
```

如果你正在一个阻塞 Java 方法里 `Thread.sleep(30s)`，框架不会神奇地杀 Java 线程；需要等控制权回到框架或由下层超时机制兜底。

所以第 25 课的 Tool timeout 与本课是配套能力。

---

## 11. JVM SIGTERM Hook

`GracefulShutdownManager` 初始化时会注册：

```text
AgentScopeJvmShutdownHook
```

收到 JVM shutdown：

```text
SIGTERM
  ↓
performGracefulShutdown()
  ↓
awaitTermination(timeout + grace period)
  ↓
最后关闭 HTTP transports
```

这正是 Kubernetes Pod 终止时需要的行为。

---

## 12. Spring Boot graceful shutdown

本节同时配置：

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

于是存在两层：

```text
Spring Boot
负责 HTTP Server / Spring Bean 生命周期

AgentScope
负责 Agent active request / state / reasoning / tool 生命周期
```

两层缺一不可。

---

## 13. 自动化测试

```bash
./mvnw -pl 26-GracefulShutdownAndRecovery test
```

测试逻辑：

```text
RUNNING
 ↓
第一次 Agent call 成功
 ↓
performGracefulShutdown()
 ↓
acceptingRequests=false
 ↓
第二次 Agent call
 ↓
AgentShuttingDownException
```

测试前后调用 `resetForTesting()`，避免全局 shutdown 状态污染其它测试。

---

## 14. Drain vs SIGTERM vs Force Kill

```text
Drain
= 停止接新 Agent 请求，进程还活着

SIGTERM
= 请求整个进程开始正常退出

SIGKILL
= 无清理机会，直接死亡
```

生产发布理想顺序：

```text
Readiness DOWN / Drain
        ↓
负载均衡停止新流量
        ↓
等待 in-flight
        ↓
SIGTERM
        ↓
Spring + AgentScope graceful shutdown
        ↓
Pod 退出
```

下一课就把这套流程真正放进 Kubernetes。
