# 45-StudioAndTraining

本课是当前课程体系的收尾：学习 AgentScope Java 2.0.1 的 **AgentScope Studio** 与 **Online Training** 生态扩展。

它们都不是“让 Agent 回答问题”的核心能力，而是围绕 Agent 生命周期提供：

```text
Studio   → 可视化调试、trace、HITL
Training → 线上采样、reward、训练闭环
```

---

## 1. 本课目标

完成后应该能回答：

1. Studio 与 OpenTelemetry 有什么区别？
2. `StudioMessageHook` 为什么适合开发期而不是默认生产全量开启？
3. `StudioUserAgent` 如何把真人输入放进 Agent 流程？
4. TrainingRunner 为什么通过 system hook 对业务调用透明？
5. 什么是 training selection strategy？
6. SamplingRate 与 ExplicitMarking 适合什么场景？
7. reward calculator 的职责是什么？
8. Trinity 在训练闭环里负责什么？
9. 为什么训练数据治理比“调用 API”本身更重要？

---

# Part A：AgentScope Studio

## 2. Studio 是什么

AgentScope Studio 是独立的可视化平台。

Java 侧通过：

```text
agentscope-extensions-studio
```

连接它。

核心类型：

```text
StudioManager
StudioConfig
StudioClient
StudioWebSocketClient
StudioMessageHook
StudioUserAgent
```

---

## 3. 工作链路

```text
Agent call
   ↓
StudioMessageHook
   ↓
StudioClient
   ↓ HTTP / WebSocket
AgentScope Studio
   ↓
trace / message / HITL UI
```

初始化：

```java
StudioManager.init()
    .studioUrl("http://localhost:8000")
    .project("MyProject")
    .runName("experiment-001")
    .initialize()
    .block();
```

然后把 Hook 挂到 Agent：

```java
ReActAgent.builder()
    .hook(new StudioMessageHook(StudioManager.getClient()))
    .build();
```

---

## 4. Studio 与 OpenTelemetry 不重复

第 24 课：

```text
OpenTelemetry
= 标准观测协议
= span / trace / exporter
= 对接企业 observability stack
```

第 45 课：

```text
AgentScope Studio
= Agent 开发/调试 UI
= 消息可视化
= Agent trace tree
= 人工输入
```

生产基础设施通常仍然以 OTel 为主。

Studio 更像 Agent 开发工作台。

---

## 5. Human-in-the-Loop

Studio 不只是看日志。

通过：

```text
StudioUserAgent
requestUserInput
```

可以形成：

```text
Agent
 ↓
需要人工回答
 ↓
Studio UI 弹框
 ↓
Human input
 ↓
Agent continue
```

这和第 08 / 31 课的 HITL 属于同一个大主题，但交互入口不同。

---

## 6. 为什么生产默认关闭 Studio Hook

如果每个生产请求都复制一份到 Studio：

```text
成本
网络依赖
数据隐私
敏感 Tool 参数
额外延迟
```

都会上升。

推荐：

```text
local/dev → enabled
staging   → selected
prod      → disabled or sampled
```

Spring 可以用：

```java
@ConditionalOnProperty("agentscope.studio.enabled")
```

控制。

---

# Part B：Online Training

## 7. TrainingRunner

官方 artifact：

```text
agentscope-extensions-training
```

核心控制器：

```text
TrainingRunner
```

职责：

```text
请求选择
  ↓
training route
  ↓
trajectory collection
  ↓
reward
  ↓
feedback
  ↓
periodic commit
  ↓
Trinity
```

---

## 8. 最小配置

```java
TrainingRunner runner = TrainingRunner.builder()
    .trinityEndpoint("http://localhost:8080")
    .modelName("/models/qwen")
    .selectionStrategy(SamplingRateStrategy.of(0.1))
    .rewardCalculator(agent -> 0.0)
    .commitIntervalSeconds(300)
    .build();
```

注意：

```text
build()
!=
start()
```

`build()` 只组装 TrainingConfig/Client/Router。

`start()` 才会：

```text
AgentBase.addSystemHook(router)
启动 commit scheduler
```

本课测试只 `build()`，绝不 `start()`。

---

## 9. SamplingRateStrategy

```java
SamplingRateStrategy.of(0.10)
```

含义：

```text
约 10% 请求进入 training route
```

适合：

```text
大流量
随机抽样
线上持续学习实验
```

采样率不是越大越好。

训练成本、偏差和数据治理都要考虑。

---

## 10. ExplicitMarkingStrategy

另一种策略：

```text
只有显式标记的请求进入训练
```

适合：

```text
人工精选样本
已知 bad case
高价值任务
实验流量
```

比纯随机采样更可控，但数据覆盖会窄。

---

## 11. RewardCalculator

训练样本不能只有输入输出，还需要评价。

```text
trajectory
   ↓
RewardCalculator
   ↓
score
```

可以根据：

```text
任务是否完成
Tool 是否正确
用户反馈
结构化约束
业务 KPI
人工评分
```

产生 reward。

本课使用：

```java
agent -> 0.0
```

只是为了验证 Builder 契约，不代表生产 reward 设计。

---

## 12. Trinity

TrainingRunner 的后端是 Trinity 或兼容服务。

它负责：

```text
训练请求
feedback
commit
训练任务
```

AgentScope Java 负责把线上 Agent 运行轨迹接到训练后端。

---

## 13. Training 对业务为什么透明

`runner.start()` 后会把 TrainingRouter 注册为 AgentBase system hook。

因此业务仍然：

```java
agent.call(msg)
```

但内部可能：

```text
normal path
or
training path
```

这是一种横切能力，和 Middleware / tracing 的思路类似。

---

## 14. 为什么本课不启动 TrainingRunner

一旦 `start()`：

```text
全局 system hook 生效
后台 commit scheduler 可能启动
选中的流量可能访问 Trinity
```

Unit/Contract Test 不应该污染 JVM 全局状态，也不应该偷偷发网络请求。

所以本课明确测试：

```text
runner.isRunning() == false
```

---

## 15. Studio + Training

两者可以组合：

```text
Production Agent
   │
   ├── Studio sampled trace
   │
   └── Training sampled trajectory
            ↓
          reward
            ↓
         Trinity
```

Studio 更适合“看见问题”。

Training 更适合“用数据改进模型”。

---

## 16. 数据治理

上线在线训练前至少回答：

```text
哪些用户数据允许训练？
是否包含 PII？
是否需要脱敏？
是否需要用户同意？
数据保存多久？
Tool 参数能不能进入 trajectory？
人工标注如何审计？
```

不要把“可以采样”理解成“所有生产流量都应该拿去训练”。

---

## 17. Reward Hacking

错误 reward 会把模型优化到错误方向。

例如：

```text
reward = 回答越长越高
```

模型可能学会：

```text
无意义地写更长
```

因此 reward 设计必须和真实业务目标对齐，并建立离线评估集。

---

## 18. 启动

```bash
./mvnw -pl 45-StudioAndTraining spring-boot:run
```

查看生态组件：

```bash
curl http://localhost:18081/api/ecosystem/components
```

查看 Training 配置预览：

```bash
curl http://localhost:18081/api/ecosystem/training-preview
```

返回中：

```text
running = false
```

因为我们没有启动训练管线。

---

## 19. 自动化测试

```bash
./mvnw -pl 45-StudioAndTraining test
```

测试包含：

```text
Studio 官方类型在 classpath
Training 官方类型在 classpath
SamplingRateStrategy 的概率配置
TrainingRunner build 不启动 pipeline
```

不需要：

```text
Studio Server
Trinity Server
模型 API Key
```

---

## 20. 01～45 的最终闭环

现在整个课程形成：

```text
Agent Core
   ↓
Harness
   ↓
Tools / Skills / RAG / Memory
   ↓
Protocol / Channel
   ↓
Distributed / K8s / Observability
   ↓
Enterprise Infrastructure
   ↓
Studio / Training
```

也就是从：

```text
“写一个 Agent”
```

一路学到：

```text
“如何把 Agent 做成企业级、可运营、可治理、可迭代的系统”
```

---

## 21. 本课结论

Studio 和 Training 都不应该被塞进核心业务逻辑。

正确位置是：

```text
Agent Runtime
   │
   ├── Observability / Studio
   └── Data Flywheel / Training
```

保持它们可插拔，生产系统才不会被实验工具链绑死。
