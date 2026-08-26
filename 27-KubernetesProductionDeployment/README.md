# 27-KubernetesProductionDeployment：把 AgentScope 真正放进 Kubernetes

## 1. 这不是“写一个 Deployment YAML”

前面已经分别学过：

```text
多用户并发
Interrupt
StateStore
Workspace
Sandbox
Gateway
Agent Protocol
DistributedStore
Tracing
ExecutionConfig
Graceful Shutdown
```

本节把这些生产能力组合起来。

目标架构：

```text
                Kubernetes Service
                       │
             ┌─────────┴─────────┐
             ▼                   ▼
          Pod A                 Pod B
        AgentScope             AgentScope
             │                   │
             └────────┬──────────┘
                      ▼
                    Redis
             ┌────────┼────────┐
             ▼        ▼        ▼
        AgentState  Workspace  coordination
```

---

## 2. 学习目标

学完应能解释：

- 为什么多 Pod 不能继续用本地 JSON StateStore；
- `distributed` profile 做什么；
- 为什么业务端口和 management 端口分开；
- startup / liveness / readiness probe 分别解决什么；
- AgentScope Drain 为什么要进入 readiness；
- Kubernetes Pod 终止时 preStop + SIGTERM 的完整顺序；
- 为什么 `terminationGracePeriodSeconds` 必须大于 Agent shutdown timeout；
- HPA 为什么不能只看“请求数量”；
- PDB 解决什么；
- RollingUpdate 为什么使用 `maxUnavailable: 0`；
- Secret 为什么不能写进仓库真实值。

---

## 3. 本地模式与 distributed 模式

默认本地启动：

```bash
./mvnw -pl 27-KubernetesProductionDeployment spring-boot:run
```

没有 Redis 也能学习接口和 health probe。

Kubernetes 使用：

```text
SPRING_PROFILES_ACTIVE=distributed
REDIS_URL=redis://...
```

`RedisConfiguration` 只在 distributed profile 创建 `JedisPooled`。

---

## 4. 一步步创建生产 Agent

### Step 1：先创建普通 HarnessAgent Builder

```java
HarnessAgent.Builder builder = HarnessAgent.builder()
        .name("kubernetes-production-agent")
        .model(model)
        .workspace(Paths.get(".agentscope/workspace"));
```

### Step 2：检测 distributed backend

当 `JedisPooled` Bean 存在：

```java
RedisDistributedStore distributedStore =
        RedisDistributedStore.fromJedis(jedis, "learn-agentscope:");
```

### Step 3：一次性接入共享能力

```java
builder.distributedStore(distributedStore)
       .filesystem(new RemoteFilesystemSpec()
               .isolationScope(IsolationScope.USER));
```

于是至少解决：

```text
AgentState -> Redis
Workspace shared routes -> Redis
```

如果未来改成 Docker/K8s Sandbox，同一个 DistributedStore 还能继续给 snapshot / guard 提供后端。

---

## 5. 为什么 management 单独使用 18082

业务流量：

```text
18081
```

运维控制面：

```text
18082
```

Service 只暴露业务端口：

```yaml
ports:
  - port: 80
    targetPort: http
```

management 端口只供 Pod probe、运维网络访问。

生产上还应该配 NetworkPolicy / Spring Security，进一步限制访问来源。

---

## 6. 三种 Probe

### startupProbe

解决：

```text
应用还在启动
≠
应用已经死了
```

模型 SDK、Redis、Spring Context 初始化可能需要时间，因此启动期先由 startupProbe 接管。

### livenessProbe

回答：

```text
这个进程是不是已经坏到需要 Kubernetes 重启？
```

不要把短暂下游 429 当成 liveness DOWN，否则容易形成重启风暴。

### readinessProbe

回答：

```text
这个 Pod 现在应该不应该继续接业务流量？
```

这正好应该和 AgentScope Drain 对齐。

---

## 7. 自定义 AgentScope Readiness

本节增加：

```java
AgentScopeReadinessHealthIndicator
```

核心判断：

```java
manager.isAcceptingRequests()
```

RUNNING：

```text
UP
```

Drain 后：

```text
DOWN
```

并加入 Spring readiness group：

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState,agentScopeReadiness
```

因此：

```text
AgentScope Drain
      ↓
acceptingRequests=false
      ↓
readiness DOWN
      ↓
Kubernetes 停止把新请求送进 Pod
```

---

## 8. Pod 终止全过程

Deployment 的 `preStop`：

```yaml
preStop:
  exec:
    command:
      - /bin/sh
      - -c
      - >-
        curl -fsS -X POST
        "http://127.0.0.1:18082/actuator/agentscope-drain?confirm=yes&token=${AGENTSCOPE_ADMIN_TOKEN}"
        || true; sleep 5
```

完整过程：

```text
Kubernetes 开始终止 Pod
        ↓
preStop 调 AgentScope Drain
        ↓
readiness = DOWN
        ↓
等待 5 秒给 Endpoint / LB 传播
        ↓
preStop 完成
        ↓
Kubernetes SIGTERM
        ↓
Spring Boot graceful shutdown
        +
AgentScopeJvmShutdownHook
        ↓
等待 active Agent requests
        ↓
保存必要状态
        ↓
Pod 退出
```

---

## 9. terminationGracePeriodSeconds 怎么算

本节 AgentScope：

```text
shutdownTimeout = 30s
```

Spring：

```text
timeout-per-shutdown-phase = 40s
```

Kubernetes：

```yaml
terminationGracePeriodSeconds: 60
```

还要算上：

```text
preStop sleep = 5s
AgentScope JVM interrupt grace = 5s
```

所以不能只写一个刚好等于 shutdown timeout 的 Kubernetes grace period。这里使用 60 秒，给 preStop、Spring shutdown、AgentScope shutdown 和调度抖动留出额外余量。

原则：

```text
K8s grace
>
preStop 时间 + 应用需要的正常退出时间
```

否则 grace 到期后 Kubernetes 会进入强制终止，你前面做的优雅下线全部失去意义。

实际项目要根据最长 Tool / Model budget 重新计算，不要机械复制 60 秒。

---

## 10. RollingUpdate

```yaml
rollingUpdate:
  maxUnavailable: 0
  maxSurge: 1
```

发布时：

```text
先多起一个新 Pod
       ↓
新 Pod readiness UP
       ↓
再下线旧 Pod
```

适合不希望发布过程中主动降低可用副本数的服务。

---

## 11. 资源限制

```yaml
requests:
  cpu: 500m
  memory: 768Mi
limits:
  cpu: "2"
  memory: 2Gi
```

这些数字只是教学起点。

真实容量必须来自：

```text
并发 session 数
上下文大小
Tool 类型
模型网络等待
JVM heap
Sandbox 使用方式
压测
```

不要把示例值直接当生产容量结论。

---

## 12. HPA

示例：

```text
min = 2
max = 6
CPU target = 60%
```

为什么设置 scaleDown stabilization：

Agent 请求可能持续数十秒甚至数分钟。

如果负载刚下降就快速缩容，容易频繁触发 drain。

因此：

```yaml
scaleDown:
  stabilizationWindowSeconds: 300
```

生产进一步可以使用自定义指标：

```text
active agent requests
queue depth
model waiting tasks
session concurrency
```

它们往往比纯 CPU 更能代表 Agent 服务真实压力。

---

## 13. PDB

```yaml
minAvailable: 1
```

防止节点维护等 voluntary disruption 一次把所有副本赶走。

注意：PDB 不是高可用魔法。

它不能阻止：

```text
节点突然断电
程序 Crash
依赖全面故障
```

---

## 14. Secret

仓库只放：

```text
secret.example.yaml
```

真实：

```text
DASHSCOPE_API_KEY
REDIS_URL
AGENTSCOPE_ADMIN_TOKEN
```

应该来自：

```text
External Secrets
Vault
云厂商 Secret Manager
Kubernetes Secret + GitOps 加密方案
```

不要把真实值提交 Git。

---

## 15. 构建镜像

从仓库根目录：

```bash
docker build \
  -f 27-KubernetesProductionDeployment/Dockerfile \
  -t learn-agentscope-demo:27 .
```

Dockerfile 是 multi-stage build，并安装 curl 用于 preStop drain。

---

## 16. 部署顺序

先准备 Secret：

```bash
kubectl apply -f 27-KubernetesProductionDeployment/k8s/secret.example.yaml
```

注意先把示例值替换掉，真实生产不要直接使用这个文件管理明文 Secret。

然后：

```bash
kubectl apply -f 27-KubernetesProductionDeployment/k8s/service.yaml
kubectl apply -f 27-KubernetesProductionDeployment/k8s/deployment.yaml
kubectl apply -f 27-KubernetesProductionDeployment/k8s/pdb.yaml
kubectl apply -f 27-KubernetesProductionDeployment/k8s/hpa.yaml
```

查看：

```bash
kubectl get pods
kubectl get hpa
kubectl get pdb
```

---

## 17. 手动验证 Readiness / Drain

本地启动后 management 在：

```text
http://localhost:18082
```

Readiness：

```bash
curl http://localhost:18082/actuator/health/readiness
```

Drain：

```bash
curl -X POST \
  'http://localhost:18082/actuator/agentscope-drain?confirm=yes&token=local-demo-token'
```

再次检查 readiness，应该变成 DOWN。

---

## 18. 自动化测试

```bash
./mvnw -pl 27-KubernetesProductionDeployment test
```

测试验证：

```text
RUNNING -> readiness UP
Drain   -> readiness DOWN
```

这正是 K8s 流量摘除的关键契约。

---

## 19. 生产架构检查单

上线前至少确认：

```text
[ ] RuntimeContext 有 userId + sessionId
[ ] AgentStateStore 不是本地文件
[ ] Workspace 需要共享的内容进入 Remote/Sandbox backend
[ ] Tool 有 timeout
[ ] Model 有 retry/backoff
[ ] Tool retry 已验证幂等性
[ ] readiness 与 Drain 联动
[ ] termination grace 足够
[ ] OTel / metrics 已接入
[ ] 管理端口未暴露公网
[ ] admin write endpoint 有鉴权
[ ] API Key / Redis 密钥不在 Git
[ ] HPA scale-down 不会频繁打断长请求
[ ] PDB 已设置
```

---

## 20. 到这里完成了什么

从第 01 课：

```text
new ReActAgent()
```

现在已经走到：

```text
Kubernetes
├── 多副本
├── 共享状态
├── readiness / liveness
├── graceful shutdown
├── rolling update
├── autoscaling
├── PDB
├── secrets
└── observability-ready
```

这已经是一条完整的 AgentScope Java 工程化学习主线。
