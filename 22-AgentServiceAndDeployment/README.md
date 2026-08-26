# 22-AgentServiceAndDeployment：把 HarnessAgent 变成可远程调度的服务

## 1. 为什么这一课不再自己设计 `/api/chat`

前面一直是：

```text
HTTP Controller
      ↓
agent.call()
```

这适合学习，但服务化以后还需要统一解决：

```text
任务提交
任务状态
等待结果
取消任务
SSE 事件流
HITL 恢复
远程 SubAgent 调用
```

AgentScope Java 2.0.1 已经提供 `agentscope-extensions-agent-protocol`，可以把 `HarnessAgent` 直接暴露成标准任务 HTTP 服务。

---

## 2. 本节目标

学完你应能解释：

- Agent Protocol 在系统中解决什么问题；
- 为什么它和第 21 课的 Gateway/Channel 不是同一层；
- 为什么引入 extension 后不需要自己手写 `/tasks` Controller；
- `POST /tasks`、`GET /tasks/{id}`、`/wait`、`/cancel`、`/events`、`/resume` 分别做什么；
- 为什么服务实例可以作为远程 SubAgent 的托管端；
- 为什么生产部署不能只考虑 JAR，还要考虑状态、工作区、权限、健康检查和滚动发布。

---

## 3. 协议分层

```text
浏览器 / Chat UI
      ↓
Gateway + Channel
      ↓
用户交互型 Agent

--------------------------

父 Harness / CI / 调度平台
      ↓
Agent Protocol HTTP
      ↓
/tasks
      ↓
远程 HarnessAgent 服务
```

可以先记：

```text
Channel/Gateway = 面向交互入口
Agent Protocol   = 面向远程任务调度
```

---

## 4. 项目结构

```text
22-AgentServiceAndDeployment
├── .agentscope/workspace/AGENTS.md
├── Dockerfile
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/agentserviceanddeployment
    │   │   ├── AgentServiceAndDeploymentApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   └── web/ServiceInfoController.java
    │   └── resources/application.yml
    └── test
        └── java/com/example/agentscope/agentserviceanddeployment
            └── AgentProtocolAutoConfigurationTest.java
```

---

## 5. 一步步编码

### Step 1：加入 Agent Protocol extension

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-agent-protocol</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

它是 Spring Boot 自动配置模块。

### Step 2：准备 HarnessAgent Bean

```java
HarnessAgent.builder()
        .name("agent-service")
        .model(model)
        .workspace(Paths.get(".agentscope/workspace"))
        .checkRunning(false)
        .build();
```

这里显式 `checkRunning(false)`，原因是 Agent Protocol 的任务可能并发到达。

真正的会话隔离仍然依靠：

```text
RuntimeContext
(userId, sessionId)
```

### Step 3：暴露 WorkspaceManager Bean

Agent Protocol 自动配置要求 Spring 容器中同时存在：

```text
HarnessAgent
WorkspaceManager
```

所以：

```java
@Bean
WorkspaceManager protocolWorkspaceManager(HarnessAgent agent) {
    return agent.getWorkspaceManager();
}
```

### Step 4：打开自动配置

```yaml
agentscope:
  agent-protocol:
    enabled: true
    streaming-enabled: true
    hitl-enabled: true
```

此时 extension 自动注册任务端点，不需要你写 TaskController。

---

## 6. 任务端点

### 提交任务

```text
POST /tasks
```

示例：

```bash
curl -X POST http://localhost:18081/tasks \
  -H 'Content-Type: application/json' \
  -d '{
    "task_id":"task-demo-1",
    "agent_id":"agent-service",
    "input":"用三点说明 AgentScope Harness 的作用",
    "context":{
      "user_id":"alice",
      "parent_session_id":"parent-session-1",
      "stream":true,
      "detail":"full"
    }
  }'
```

### 查询快照

```bash
curl http://localhost:18081/tasks/task-demo-1
```

### 等待终态

```bash
curl 'http://localhost:18081/tasks/task-demo-1/wait?timeout_seconds=60'
```

### 取消

```bash
curl -X POST http://localhost:18081/tasks/task-demo-1/cancel
```

### SSE 事件流

```bash
curl -N http://localhost:18081/tasks/task-demo-1/events
```

Agent Protocol 支持通过 `from_seq` 或 `Last-Event-ID` 续订事件。

### HITL 恢复

当远程 Agent 因权限 ASK 暂停时：

```text
POST /tasks/{taskId}/resume
```

请求中提交每个 `toolCallId` 的批准/拒绝结果。

---

## 7. 和 Remote SubAgent 的关系

第 16 课学过：

```java
SubagentDeclaration.builder()
    .url("http://remote-agent:18081")
    .build();
```

这个远程地址的服务端，正可以由本节 Agent Protocol extension 承担。

于是链路变成：

```text
Parent HarnessAgent
        ↓
RemoteSubagentStub
        ↓ HTTP
Agent Protocol
        ↓
Remote HarnessAgent
```

这就是从“进程内 SubAgent”走向“服务间 Agent 编排”的关键一步。

---

## 8. Docker 部署

先打包：

```bash
./mvnw -pl 22-AgentServiceAndDeployment package
```

构建镜像：

```bash
cd 22-AgentServiceAndDeployment
docker build -t agentscope-agent-service:22 .
```

启动：

```bash
docker run --rm \
  -p 18081:18081 \
  -e DASHSCOPE_API_KEY="$DASHSCOPE_API_KEY" \
  agentscope-agent-service:22
```

---

## 9. 真正生产部署还缺什么

Docker 化不等于生产完成。

至少还要考虑：

```text
多副本状态共享
Workspace 共享
滚动发布
任务取消
优雅停机
超时
限流
权限
密钥注入
日志与 Trace
```

所以本节故意不把这些全塞进来。

下一节专门解决：

```text
23-DistributedStateAndStorage
```

---

## 10. 自动化测试

测试使用 `ApplicationContextRunner`，不调用真实模型。

验证：

```text
agentscope.agent-protocol.enabled=true
        ↓
AgentProtocolTaskEventBus Bean
AgentProtocolTaskStore Bean
AgentProtocolController Bean
```

关闭配置后这些 Bean 不会创建。

执行：

```bash
./mvnw -pl 22-AgentServiceAndDeployment test
```

---

## 11. 本节边界

本节只学习：

```text
HarnessAgent
   ↓
Agent Protocol
   ↓
独立任务服务
   ↓
JAR / Docker 部署
```

不深入：

- Redis/MySQL 分布式状态；
- Kubernetes Deployment；
- Service Mesh；
- OpenTelemetry Collector；
- 自动扩缩容。

这些会在后续课程逐步补齐。
