# 第 39 课：SandboxProviders —— Docker / Kubernetes / E2B / Daytona / AgentRun

> 本课目标：理解 AgentScope Java 2.0.1 如何用同一个 `SandboxFilesystemSpec` 抽象切换五种隔离执行后端，并明确“Agent 服务部署在哪里”和“Agent 执行用户代码在哪里”是两个不同问题。

---

## 1. 先解决最容易混淆的问题

第 27 课学的是：

```text
HarnessAgent 服务
      |
      v
Kubernetes Deployment
```

也就是：

> **Agent 服务自己运行在哪里？**

本课学的是：

```text
HarnessAgent
      |
      | execute / write_file / npm / python
      v
Sandbox
      |
      +-- Docker
      +-- Kubernetes Sandbox
      +-- E2B
      +-- Daytona
      `-- AgentRun
```

也就是：

> **Agent 要执行的不可信命令运行在哪里？**

两个 K8s 概念不要混在一起。

---

## 2. 为什么需要 Sandbox

如果 Agent 直接在宿主机执行：

```bash
rm -rf ...
pip install ...
npm install ...
python user_script.py
```

它会直接影响 Agent 服务所在机器。

Sandbox 增加一道执行边界：

```text
LLM
 |
Tool
 |
Harness Filesystem
 |
SandboxFilesystemSpec
 |
隔离执行环境
```

这样文件与 shell 行为不直接发生在宿主进程环境中。

---

## 3. 五种 Provider

| Provider | Artifact | 典型场景 |
| --- | --- | --- |
| Docker | `agentscope-harness` 内置 | 本地开发 / 单机 |
| Kubernetes | `agentscope-extensions-sandbox-kubernetes` | 自建 K8s / agent-sandbox |
| E2B | `agentscope-extensions-sandbox-e2b` | 托管开发沙箱 |
| Daytona | `agentscope-extensions-sandbox-daytona` | 通用托管 Sandbox API |
| AgentRun | `agentscope-extensions-sandbox-agentrun` | 阿里云 AgentRun / NAS / OSS |

它们最终都属于：

```java
SandboxFilesystemSpec
```

因此 Harness 上层代码保持一致。

---

## 4. 本模块为什么默认不真的创建 Sandbox

如果启动测试就真正创建：

```text
Docker container
Kubernetes SandboxClaim
E2B sandbox
Daytona sandbox
AgentRun instance
```

课程会被本机环境、集群、账号和云凭证绑死。

所以本课的 contract test 验证：

```text
扩展模块存在
Spec API 正确
配置对象可以构造
五种 Provider 都落到 SandboxFilesystemSpec
```

**只有真正调用 Harness 并首次使用 filesystem 时，才进入 client / sandbox 生命周期。**

因此默认启动：

```bash
./mvnw -pl 39-SandboxProviders spring-boot:run
```

不要求任何云凭证。

---

## 5. 查看 Provider 清单

```bash
curl http://localhost:18081/api/sandboxes/providers
```

返回会告诉你：

- artifact
- Spec class
- control plane
- credential
- 推荐场景

构造一个示例 Spec：

```bash
curl http://localhost:18081/api/sandboxes/spec/e2b
```

这里只创建 Java 配置对象，不连接 E2B。

---

## 6. Docker：本地最简单

```java
DockerFilesystemSpec spec = new DockerFilesystemSpec()
    .image("ubuntu:24.04")
    .memorySizeBytes(512L * 1024 * 1024)
    .cpuCount(1L)
    .workspaceRoot("/workspace");

spec.isolationScope(IsolationScope.SESSION);
```

适合：

```text
开发机
CI 单机 Runner
内部可信环境
```

优点：

- 不依赖云平台
- 易调试
- 成本直观

缺点：

- Docker daemon 本身是宿主级资源
- 多节点调度需要你自己解决

---

## 7. Kubernetes Sandbox：不是普通 Deployment

2.0.1 的 Kubernetes Sandbox 基于：

```text
kubernetes-sigs/agent-sandbox
```

核心概念包括：

```text
SandboxClaim
WarmPool
runtime HTTP API
```

示例：

```java
KubernetesFilesystemSpec spec = new KubernetesFilesystemSpec()
    .namespace("agentscope")
    .warmPoolName("agentscope-warm-pool")
    .workspaceRoot("/workspace")
    .fileApiBaseDir("/workspace")
    .serverPort(8888);
```

它不是：

```text
kubectl exec <agent-service-pod>
```

而是 AgentScope 创建/领取独立 Sandbox 实例，然后通过运行时 HTTP API 与它交互。

---

## 8. Kubernetes runtime image 的额外契约

K8s agent-sandbox 运行时通常需要提供：

```text
POST /execute
POST /upload
GET  /download/{path}
GET  /list/{path}
GET  /exists/{path}
```

其中 `/execute` 必须支持真正的 POSIX shell 语义，因为 Harness 会发送：

```text
cd ... && (...)
管道
重定向
heredoc
```

所以不能随便找一个只支持“单命令数组”的 runtime image。

---

## 9. E2B

2.0.1 的配置对象：

```java
E2bFilesystemSpec spec = new E2bFilesystemSpec()
    .apiKey(System.getenv("E2B_API_KEY"))
    .templateId("base")
    .workspaceRoot("/workspace")
    .sandboxTimeoutSeconds(600)
    .connectTimeoutSeconds(30)
    .readTimeoutSeconds(60)
    .maxRetries(2);
```

还支持：

```text
apiBaseUrl
domain
runUser
persistenceMode
codec
snapshotSpec
workspaceSpec
```

适合不想自建 Sandbox 集群，但需要标准隔离开发环境的场景。

---

## 10. Daytona

配置示例：

```java
DaytonaFilesystemSpec spec = new DaytonaFilesystemSpec()
    .apiKey(System.getenv("DAYTONA_API_KEY"))
    .image("ubuntu:24.04")
    .cpu(1)
    .memory(2)
    .disk(5)
    .workspaceRoot("/workspace");
```

还可以指定：

```text
controlPlaneBaseUrl
toolboxBaseUrl
snapshotId
```

它更像一个通用 Sandbox control plane。

---

## 11. AgentRun

AgentRun 是阿里云托管 Sandbox：

```java
AgentRunFilesystemSpec spec = new AgentRunFilesystemSpec()
    .apiKey(System.getenv("AGENTRUN_API_KEY"))
    .accountId(accountId)
    .region("cn-hangzhou")
    .templateName("agentscope-demo")
    .workspaceRoot("/workspace")
    .sandboxIdleTimeoutSeconds(600);
```

2.0.1 还支持：

```text
NAS mount
OSS mount
MCP server URL
MCP endpoint
Data Plane base URL
```

适合阿里云内网与存储生态已经比较成熟的场景。

---

## 12. IsolationScope 对所有 Provider 都一样

这正是抽象统一的价值。

```text
SESSION
USER
AGENT
GLOBAL
```

不是 Docker 专属能力，也不是 K8s 专属能力。

本课所有示例统一使用：

```java
spec.isolationScope(IsolationScope.SESSION);
```

因为课程测试希望：

```text
每个 session 一份独立环境
行为最容易推理
```

生产 SaaS 往往会考虑 `USER`。

---

## 13. Snapshot 也属于统一抽象

除了 E2B 等平台可能有原生持久化能力，Harness 还可以使用统一：

```text
SandboxSnapshotSpec
```

典型后端：

```text
NoopSnapshotSpec
LocalSnapshotSpec
RedisSnapshotSpec
JdbcSnapshotSpec
OssSnapshotSpec
```

流程：

```text
call 开始
  |
  +-- sandbox 仍活着 -> 复用
  |
  +-- sandbox 已消失 -> snapshot restore
  |
  `-- 无 snapshot -> 冷启动 Workspace
```

第 38 课的分布式后端正好可以接到这里。

---

## 14. SandboxExecutionGuard

当：

```text
Pod A
Pod B
```

同时处理同一个共享 Sandbox slot 时，需要避免并发覆盖。

尤其：

```text
IsolationScope.USER
IsolationScope.AGENT
IsolationScope.GLOBAL
```

可以通过：

```text
RedisSandboxExecutionGuard
JdbcSandboxExecutionGuard
```

做分布式互斥。

所以第 38、39 课实际是连续的一条生产链路。

---

## 15. 镜像不是随便选

Harness 文件工具依赖 Sandbox 中存在：

```text
sh
mkdir
dirname
rm
mv
test
printf
sort
sed
grep
find
stat -c
tar
base64
python3
```

并要求 workspace 可写。

因此：

```text
ubuntu / debian 基础镜像
```

通常更适合教学和通用 Agent 工具。

而：

```text
alpine / distroless
```

不能默认认为兼容。

---

## 16. Provider 应该怎么选

### 本地开发

```text
Docker
```

### 公司已有成熟 Kubernetes

```text
Kubernetes Sandbox
```

前提：愿意部署并维护 agent-sandbox runtime。

### 不想维护 Sandbox 基础设施

```text
E2B / Daytona
```

### 阿里云生态

```text
AgentRun
```

尤其已经使用 NAS / OSS / RAM 时。

---

## 17. 成本不是只有 CPU

选择 Provider 要同时评估：

```text
冷启动时间
Warm Pool 成本
容器空闲时间
Snapshot 存储
网络出口
镜像拉取
并发 Sandbox 数
安全隔离等级
```

不能只比较每小时 CPU 单价。

---

## 18. 安全边界

Sandbox 会显著缩小风险，但仍然要配置：

- CPU limit
- memory limit
- disk limit
- network policy
- credentials
- workspace root
- timeout
- idle timeout
- image allowlist

最重要的是：

```text
不要把宿主机高权限 Secret
无条件注入用户可执行的 Sandbox
```

---

## 19. 自动化测试

```bash
./mvnw -pl 39-SandboxProviders test
```

测试验证五个真实 extension class：

```text
DockerFilesystemSpec
KubernetesFilesystemSpec
E2bFilesystemSpec
DaytonaFilesystemSpec
AgentRunFilesystemSpec
```

全部可以在不创建远端 Sandbox 的情况下完成配置对象构造。

这保证：

```text
课程测试稳定
同时又没有用假类冒充官方 API
```

---

## 20. 与前面课程的关系

```text
20-FilesystemAndSandbox
    学 Local / Remote / Sandbox 三种抽象
              |
              v
27-KubernetesProductionDeployment
    学 Agent 服务部署到 K8s
              |
              v
38-DistributedBackends
    学 Snapshot / Guard 后端
              |
              v
39-SandboxProviders
    学真正的 Sandbox 执行 Provider
```

---

## 21. 最终心智模型

```text
                    HarnessAgent
                         |
                 AbstractFilesystem
                         |
                SandboxFilesystemSpec
                         |
       +---------+-------+-------+---------+
       |         |       |       |         |
     Docker      K8s     E2B   Daytona  AgentRun
       |         |       |       |         |
       +---------+-------+-------+---------+
                         |
              same Harness file tools
                         |
     read_file / write_file / edit_file / execute
```

关键结论：

> Provider 决定 Sandbox 在哪里运行；Harness 保持同一套 Agent、Tool、Workspace 和文件操作语义。
