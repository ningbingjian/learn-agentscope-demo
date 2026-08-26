# 20-FilesystemAndSandbox：文件系统、Shell 与执行隔离

## 1. 为什么现在必须学习 Sandbox

到第 19 课为止，Agent 已经能拥有：

```text
Java Tool
MCP Tool
Skill
SubAgent
write_file / edit_file
execute shell
```

能力越强，越不能忽略一个问题：

> **这些操作究竟发生在宿主机、共享存储，还是隔离环境里？**

本节就是回答这个问题。

---

## 2. 本节目标

学完后应能解释：

- Workspace 和 Filesystem 为什么不是同一个概念？
- `LocalFilesystemSpec`、`RemoteFilesystemSpec`、`SandboxFilesystemSpec` 的职责差异；
- `LocalFsMode.ROOTED` 为什么比 unrestricted 更适合学习和生产；
- `IsolationScope.SESSION / USER / AGENT / GLOBAL` 分别按什么维度共享状态；
- Docker Sandbox 为什么能把 shell 爆炸半径关进容器；
- Sandbox snapshot 与 AgentState 是不是一回事；
- 为什么生产 Coding Agent 不应该默认在应用宿主机直接执行任意 shell。

本节不深入 Kubernetes/E2B/Daytona 等具体供应商 SDK，只用 Docker 建立核心模型。

---

# 3. 三种 Filesystem 模式

```text
HarnessAgent
    ↓
Filesystem Spec
    ├── LocalFilesystemSpec
    │      └── host filesystem + host shell
    │
    ├── RemoteFilesystemSpec
    │      └── shared KV/store, no shell
    │
    └── SandboxFilesystemSpec
           └── Docker / Kubernetes / E2B / Daytona / AgentRun
```

### Local

适合：

- 本地开发；
- CLI；
- 可信任务；
- 单机工具。

最大风险：

```text
execute
  ↓
sh -c ...
  ↓
宿主机
```

### Remote

适合：

- 多 Pod；
- 多副本共享 Memory/Session；
- Redis/JDBC KV。

它故意不提供 shell。

### Sandbox

适合：

- Coding Agent；
- 不可信命令；
- 安装依赖；
- 运行测试；
- 临时生成文件。

```text
Agent
  ↓
execute
  ↓
Docker Container
  ↓
不是 Spring Boot 宿主机
```

---

# 4. 本案例设计

为了保持模块容易启动：

```text
默认：LocalFilesystemSpec
可选：DockerFilesystemSpec
```

环境变量：

```bash
SANDBOX_DEMO_ENABLED=false   # 默认
```

切 Docker：

```bash
export SANDBOX_DEMO_ENABLED=true
```

---

# 5. 项目结构

```text
20-FilesystemAndSandbox
├── .agentscope/workspace/AGENTS.md
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/filesystemandsandbox
    │   │   ├── FilesystemAndSandboxApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   └── web/FilesystemController.java
    │   └── resources/application.yml
    └── test
        └── java/com/example/agentscope/filesystemandsandbox/FilesystemSpecContractTest.java
```

---

# 6. 一步步编码

## Step 1：先配置 Local Filesystem

```java
LocalFilesystemSpec local = new LocalFilesystemSpec()
        .mode(LocalFsMode.ROOTED)
        .isolationScope(IsolationScope.USER)
        .inheritEnv(false)
        .executeTimeoutSeconds(30)
        .maxOutputBytes(100_000);
```

### `ROOTED`

不是只允许相对路径，而是：

> Agent 使用绝对路径时，也必须落在允许的 project/workspace/additional roots 内。

这比：

```text
UNRESTRICTED
```

安全得多。

### `inheritEnv(false)`

意味着 shell 默认看不到 Spring Boot 进程全部环境变量。

这是很重要的安全习惯，因为宿主环境里可能有：

```text
DATABASE_PASSWORD
AWS_SECRET
GITHUB_TOKEN
DASHSCOPE_API_KEY
```

## Step 2：按 USER 做本地文件隔离

```java
.isolationScope(IsolationScope.USER)
```

心智模型：

```text
alice/session-a ─┐
                 ├→ alice namespace
alice/session-b ─┘

bob/session-a ───→ bob namespace
```

注意：IsolationScope 管的是 **Filesystem/Sandbox 的共享与隔离维度**，不是替代 RuntimeContext。

RuntimeContext 仍然提供：

```text
userId
sessionId
```

IsolationScope 决定从这些身份字段中选哪个来生成隔离 key。

## Step 3：增加 Docker Sandbox 配置

```java
DockerFilesystemSpec docker = new DockerFilesystemSpec()
        .image("python:3.12-slim")
        .memorySizeBytes(512L * 1024 * 1024)
        .cpuCount(1L)
        .workspaceRoot("/workspace");

docker.isolationScope(IsolationScope.SESSION);
```

这里主动使用：

```text
SESSION
```

于是：

```text
alice/session-a → sandbox state A
alice/session-b → sandbox state B
bob/session-a   → sandbox state C
```

## Step 4：运行时选择模式

```java
if (sandboxDemoEnabled) {
    builder.filesystem(docker);
} else {
    builder.filesystem(local);
}
```

Agent 上层代码完全没有变化：

```java
agent.call(...)
```

工具也还是：

```text
read_file
write_file
edit_file
execute
```

真正变化的是工具背后的执行位置。

这就是 Filesystem abstraction 的价值。

## Step 5：增加观察接口

```text
GET /api/fs/info
```

可以看到当前模式：

```json
{
  "mode": "local-rooted",
  "sandboxEnabled": false,
  "localIsolationScope": "USER",
  "sandboxIsolationScope": "SESSION"
}
```

## Step 6：通过 Agent 实验文件操作

```text
POST /api/fs/chat
```

例如让 Agent：

```text
在 workspace 下创建 notes/demo.txt，写入 hello，随后读回来。
```

在 Local 模式，它操作受 ROOTED 策略约束的本地文件系统。

在 Docker 模式，它通过 Sandbox filesystem 操作容器工作区。

## Step 7：自动化测试不启动 Docker

测试只构造：

```java
new DockerFilesystemSpec()
```

并检查：

```text
IsolationScope.SESSION
```

不会调用 `toSandboxContext()`，因此不会真的连接 Docker daemon。

---

# 7. IsolationScope 四种模式

## SESSION

```text
(userId, sessionId)
        ↓
session key
```

不同 session 相互隔离。

最适合：

- 一次性 Coding Session；
- 每个对话独立环境。

## USER

```text
userId
  ↓
shared across user's sessions
```

同一用户的多个 session 可以复用环境。

适合：

- 个人工作台；
- 用户长期工程环境。

## AGENT

同一个 Agent 的所有用户/session 共享。

生产要非常谨慎。

## GLOBAL

整个 workspace/store 共用一个槽位。

通常只有明确的全局共享场景才使用。

---

# 8. Sandbox State 和 AgentState 不一样

这一点非常关键：

```text
AgentState
├── conversation context
├── permission state
├── task state
└── plan mode state

Sandbox State
├── installed packages
├── generated files
├── working directory state
└── snapshot/reference
```

所以：

```text
会话能恢复
```

不代表：

```text
容器里的 npm install 也自动恢复
```

反过来也一样。

生产系统往往需要两套持久化同时设计。

---

# 9. Workspace Projection

Sandbox 不是完全与宿主项目失联。

Harness 可以把静态资产投影到沙箱：

```text
AGENTS.md
skills/
subagents/
knowledge/
.skills-cache/
```

因此：

```text
宿主保存 Agent 定义
      ↓ projection
沙箱负责执行
```

这是非常合理的职责分离。

---

# 10. Docker 实验

## 默认 Local

```bash
export DASHSCOPE_API_KEY="你的 Key"
./mvnw -pl 20-FilesystemAndSandbox spring-boot:run
```

查看：

```bash
curl http://localhost:18081/api/fs/info
```

调用：

```bash
curl -X POST http://localhost:18081/api/fs/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"local-1",
    "message":"请在 workspace 创建 notes/demo.txt，写入 hello-agentscope，然后读回来"
  }'
```

## Docker Sandbox

先确认：

```bash
docker version
```

然后：

```bash
export SANDBOX_DEMO_ENABLED=true
export SANDBOX_IMAGE=python:3.12-slim
./mvnw -pl 20-FilesystemAndSandbox spring-boot:run
```

再调用同样接口。

上层 REST 和 Agent 调用代码不需要改变。

---

# 11. 生产安全原则

### 原则 1

不可信 shell 不要直接在应用宿主机执行。

### 原则 2

不要默认继承全部宿主环境变量。

### 原则 3

为容器设置资源上限：

```text
CPU
Memory
Timeout
Network
```

### 原则 4

IsolationScope 需要和业务身份模型一起设计。

### 原则 5

高权限 Tool 仍然应该叠加：

```text
Permission System
+ Sandbox
```

Sandbox 不是权限系统的替代品。

---

# 12. 本节边界

本节没有展开：

- Kubernetes Sandbox Controller；
- E2B / Daytona / AgentRun；
- snapshot 后端实现；
- RemoteFilesystem + Redis/JDBC。

它们都建立在本节的同一个抽象上。

下一节：

```text
21-GatewayAndChannel
```

从“Agent 在哪里执行”转向“用户的消息如何真正进入 Agent 系统”。
