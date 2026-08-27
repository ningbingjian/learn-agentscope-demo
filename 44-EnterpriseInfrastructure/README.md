# 44-EnterpriseInfrastructure

本课学习 AgentScope Java 2.0.1 如何接入企业基础设施：**Scheduler、Nacos、Higress**。

它们不是三个相同类别的组件：

```text
Scheduler = 什么时候运行 Agent
Nacos     = Agent/Prompt/Skill 如何被发现和动态配置
Higress   = Tool 如何通过 AI Gateway 被治理和暴露
```

---

## 1. 本课目标

完成后应该能回答：

1. `AgentScheduler` 解决什么问题？
2. Quartz 与 XXL-Job 怎么选？
3. 为什么调度任务触发时应该创建 fresh Agent？
4. Nacos 在 AgentScope 里为什么不只是“配置中心”？
5. Nacos A2A 注册发现和第 35 课 A2A 有什么关系？
6. Prompt 热更新解决什么生产问题？
7. Nacos Skill Repository 为什么是只读？
8. Higress 与第 19 课 MCP 是什么关系？
9. 为什么 Higress 的价值主要是网关治理，而不是重新发明 Tool？

---

## 2. 总体架构

```text
                    Enterprise Infrastructure
                              │
          ┌───────────────────┼───────────────────┐
          ↓                   ↓                   ↓
      Scheduler             Nacos               Higress
          │                   │                   │
   when to execute      discovery/config     tool governance
          │                   │                   │
          └───────────────────┼───────────────────┘
                              ↓
                           Agent
```

---

# Part A：Scheduler

## 3. AgentScheduler

官方统一抽象：

```text
AgentScheduler
├── schedule
├── pause
├── resume
├── cancel
└── shutdown
```

两种官方实现：

```text
QuartzAgentScheduler
XxlJobAgentScheduler
```

对应 artifact：

```text
agentscope-extensions-scheduler-quartz
agentscope-extensions-scheduler-xxl-job
```

公共配置放在：

```text
agentscope-extensions-scheduler-common
```

---

## 4. ScheduleConfig

调度策略不是写死在 Agent 里的。

AgentScope Java 2.0.1 的 Builder 是**按策略选择方法**，不是手工调用 `scheduleMode(...)`。调用 `fixedRate / fixedDelay / cron` 时，Builder 会自动设置对应的 `ScheduleMode`。

例如固定频率：

```java
ScheduleConfig fixed = ScheduleConfig.builder()
    .fixedRate(5_000L)
    .initialDelay(1_000L)
    .build();
```

构建后：

```text
getScheduleMode() = FIXED_RATE
getFixedRate() = 5000
```

CRON：

```java
ScheduleConfig cron = ScheduleConfig.builder()
    .cron("0 0 8 * * ?")
    .build();
```

构建后：

```text
getScheduleMode() = CRON
getCronExpression() = 0 0 8 * * ?
```

还支持：

```java
ScheduleConfig.builder()
    .fixedDelay(5_000L)
    .build();
```

三者区别：

```text
FIXED_RATE
按时钟节奏触发

FIXED_DELAY
上一次执行完成后再等 delay

CRON
按日历时间表达式触发
```

---

## 5. Quartz

适合：

```text
单机
小集群
已有 Quartz JDBC JobStore
不想额外维护调度平台
```

最小：

```java
QuartzAgentScheduler scheduler = QuartzAgentScheduler.builder()
    .autoStart(true)
    .build();
```

Quartz 可以纯内存，也可以通过数据库做集群调度。

---

## 6. XXL-Job

适合：

```text
需要管理控制台
需要任务日志
需要分布式执行器
需要人工触发/暂停/路由
```

AgentScope 的 `XxlJobAgentScheduler` 是把 Agent Task 接到 XXL-Job Executor。

注意：真正的 CRON/路由策略很多是在 XXL-Job Admin 中管理，而不是 Java 代码里完全控制。

---

## 7. 为什么每次触发创建 fresh Agent

如果同一个 Agent 实例被多个定时任务并发复用：

```text
任务 A state
任务 B state
任务 C context
```

容易互相污染。

Scheduler 的设计目标是：

```text
trigger
   ↓
create Agent
   ↓
execute
   ↓
finish
```

状态是否持久化应该交给前面学过的 StateStore/DistributedStore，而不是靠“永远不销毁同一个 Agent 对象”。

---

# Part B：Nacos

## 8. Nacos 在 AgentScope 中的三个角色

AgentScope Java 2.0.1 提供：

```text
agentscope-extensions-nacos-a2a
agentscope-extensions-nacos-prompt
agentscope-extensions-nacos-skill
```

### A2A

```text
Agent A
  ↓ register AgentCard
Nacos AI Service
  ↓ discovery
Agent B / A2aAgent
```

核心类：

```text
NacosA2aRegistry
NacosAgentCardResolver
```

这不是替代 A2A。

```text
A2A
= 通信协议

Nacos
= 服务注册发现
```

---

## 9. Prompt 热更新

核心类：

```text
NacosPromptListener
```

场景：

```text
system prompt 需要调整
      ↓
Nacos 发布新版本
      ↓
listener 本地缓存刷新
      ↓
下一次请求立即使用新 prompt
```

不需要：

```text
改 application.yml
build image
rolling restart
```

但是要注意 Prompt 也是生产配置：

```text
版本
审核
灰度
回滚
审计
```

不能因为“Prompt 是文本”就跳过变更治理。

---

## 10. Nacos Skill

第 42 课已经学过：

```text
NacosSkillRepository
```

2.0.1 中它是：

```text
read-only
```

主要负责从 Nacos AI Skill Package 下载 ZIP 并解析。

这节把它放回 Nacos 总体架构里理解。

---

# Part C：Higress

## 11. Higress 不是另一套 Tool API

第 19 课：

```text
Agent
 ↓
MCP Client
 ↓
MCP Server
```

第 44 课：

```text
Agent
 ↓
HigressMcpClientWrapper
 ↓
Higress AI Gateway
 ↓
MCP Tools
```

核心类：

```text
HigressMcpClientBuilder
HigressMcpClientWrapper
HigressToolkit
```

---

## 12. Higress 的价值

把下面这些问题移到网关：

```text
认证
限流
路由
配额
Tool search
可观测
```

Agent 只保留：

```text
我当前能看到哪些 Tool？
我什么时候调用？
```

这和第 30 课 Tool Surface 的思想一致。

---

## 13. 选择性启用 Tool

```java
toolkit.registration()
    .mcpClient(client)
    .enableTools(List.of("search-doc", "fetch-url"))
    .group("knowledge")
    .apply();
```

即使 Higress 网关上有 200 个 Tool，也不代表单个 Agent 必须看到 200 个 Tool Schema。

---

## 14. 不要重复建设治理层

如果 Higress 已经负责：

```text
auth
quota
rate limit
routing
```

Agent 应用不要再复制一套互相不一致的网关策略。

但是：

```text
网关授权 != Agent 业务权限
```

高风险 Tool 仍然应该结合第 08 / 31 课的 Permission/HITL。

---

## 15. 本课代码为什么不连外部服务

默认 Web 服务只展示真实类型和 ScheduleConfig。

不会自动：

```text
启动 XXL-Job executor
连接 Nacos
连接 Higress MCP endpoint
```

原因是这些属于 Integration/E2E 测试，必须由部署环境提供真实依赖。

核心 contract test 仍然可以验证：

```text
官方类在 classpath
fixedRate() → FIXED_RATE
cron() → CRON
builder/getter 契约
```

---

## 16. 启动

```bash
./mvnw -pl 44-EnterpriseInfrastructure spring-boot:run
```

查看组件：

```bash
curl http://localhost:18081/api/infrastructure/components
```

查看调度配置样例：

```bash
curl http://localhost:18081/api/infrastructure/schedules
```

---

## 17. 自动化测试

```bash
./mvnw -pl 44-EnterpriseInfrastructure test
```

默认不依赖：

```text
Nacos Server
Higress Gateway
XXL-Job Admin
DashScope API Key
```

---

## 18. 生产落地顺序

推荐：

```text
先确认有没有现成基础设施
        ↓
复用已有 Quartz / XXL / Nacos / Gateway
        ↓
再接 AgentScope Adapter
```

不要为了“AI Agent”重新搭一遍全套中间件。

---

## 19. 本课结论

企业级 Agent 不是孤立 Java 进程。

它最终会进入已有基础设施：

```text
Scheduler → 什么时候运行
Nacos     → 去哪里找/配置什么
Higress   → Tool 如何被治理
```

AgentScope 的价值是提供 Adapter，把这些能力接回来，而不是替代企业已有平台。
