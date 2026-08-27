# 33-AdminOpsControlPlane

前面我们已经解决：

```text
Agent 怎么工作
怎么持久化
怎么部署
怎么观测
怎么优雅下线
```

这一课解决的是另一件事：

> 服务已经跑在生产环境里，运维人员如何在**不改业务接口**的情况下观察和控制 AgentScope？

答案是 AgentScope Java 2.0.1 的 `agentscope-admin-spring-boot-starter`。

## 1. Control Plane 和 Data Plane

业务流量：

```text
User
  |
  v
/api/chat
  |
  v
Agent
```

管理流量：

```text
Operator / SRE / Admin UI
          |
          v
AgentScope Admin
    +-----+------+
    |            |
 Actuator      /v1/admin
 Control       Session Data Plane
```

不要把管理接口直接塞进业务 Controller。

## 2. 为什么 Admin Starter 默认关闭

配置类的默认值：

```text
agentscope.admin.enabled = false
agentscope.admin.write-enabled = false
```

也就是说，仅仅把依赖放进 classpath：

```text
不会自动暴露管理接口
```

必须显式：

```yaml
agentscope:
  admin:
    enabled: true
```

这是正确的生产默认值。

## 3. 本课配置

```yaml
agentscope:
  admin:
    enabled: true
    write-enabled: ${AGENTSCOPE_ADMIN_WRITE_ENABLED:false}
    write-token: ${AGENTSCOPE_ADMIN_WRITE_TOKEN:}
    base-path: /v1/admin
```

课程默认仍然：

```text
write-enabled = false
```

所以可以放心先学习读控制面。

## 4. Actuator Control Plane

AgentScope 2.0.1 当前提供：

```text
agentscope-status
agentscope-agents
agentscope-tools
agentscope-models
agentscope-usage
agentscope-commands
agentscope-permissions
agentscope-subagents
agentscope-doctor
agentscope-drain
agentscope-shutdown
```

本课通过 Spring Boot Actuator 暴露它们。

## 5. status

```bash
curl http://localhost:18081/actuator/agentscope-status
```

它回答的是：

```text
Admin 是否开启
Admin 写操作是否开启
base_path
进程级 AgentScope 状态
```

这比业务自己写一个 `/status` 更好，因为它属于统一控制面。

## 6. agents

```bash
curl http://localhost:18081/actuator/agentscope-agents
```

`AgentRegistry` 会自动收集 Spring 中的 singleton Agent Bean。

本课注册：

```text
adminDemoAgent
    name = admin-demo-agent
```

生产里如果 Agent 是动态/Prototype 创建的，需要在创建时主动：

```java
agentRegistry.register(agent)
```

## 7. tools

```bash
curl http://localhost:18081/actuator/agentscope-tools
```

它按 Spring `Toolkit` Bean 展示工具。

本课有：

```text
adminDemoToolkit
  -> system_info
```

这里要注意：

```text
Agent 内部 runtime toolkit
!=
Spring 中被 inventory 的 Toolkit Bean
```

所以想在 Admin 中清楚看到工具集合，平台化项目通常会把 Toolkit 也作为受管 Bean。

## 8. models

```bash
curl http://localhost:18081/actuator/agentscope-models
```

本课会看到：

```text
adminDemoModel -> lesson-admin-demo-model
```

这对模型平台特别实用：

```text
哪个模型 Bean 正在运行？
当前有哪些模型？
模型名是什么？
```

## 9. usage

```bash
curl http://localhost:18081/actuator/agentscope-usage
```

结构按：

```text
global
by_agent
by_model
```

统计是当前 JVM 进程累计值，JVM 重启会清零。

所以它适合：

```text
实时运维观察
```

不适合直接作为：

```text
长期计费账本
```

长期计费应该把审计/usage 事件写入外部存储。

## 10. doctor

```bash
curl http://localhost:18081/actuator/agentscope-doctor
```

它解决的是：

> 系统看起来启动了，但 AgentScope 的依赖、配置和运行状态到底健康不健康？

这类 endpoint 很适合挂在内部运维平台。

## 11. commands / permissions / subagents

```bash
curl http://localhost:18081/actuator/agentscope-commands
curl http://localhost:18081/actuator/agentscope-permissions
curl http://localhost:18081/actuator/agentscope-subagents
```

它们分别观察：

```text
Admin commands
Permission 状态
SubAgent 声明/运行信息
```

## 12. Drain 和 Shutdown

```text
agentscope-drain
agentscope-shutdown
```

这两个不是普通读接口。

第 26、27 课已经用过 Drain；这一课把它放回完整 Admin 控制面理解。

不要把它们开放给普通用户网络。

## 13. Data Plane 管理 API

Starter 还自动提供 Session 管理 REST：

```text
GET /v1/admin/sessions
GET /v1/admin/sessions/{sessionId}/messages
GET /v1/admin/sessions/{sessionId}/state
GET /v1/admin/sessions/{sessionId}:export
```

以及写操作：

```text
POST /v1/admin/sessions/{sessionId}:compact
POST /v1/admin/sessions/{sessionId}:abort
POST /v1/admin/sessions/{sessionId}:undo
POST /v1/admin/sessions/{sessionId}:redo
```

还有 Plan、Task 等控制能力。

## 14. Write Guard

所有管理写操作最重要的边界是：

```text
agentscope.admin.write-enabled
```

如果为 false：

```text
403 Forbidden
```

如果启用 token：

```yaml
agentscope:
  admin:
    write-token: ${AGENTSCOPE_ADMIN_WRITE_TOKEN}
```

请求还必须带：

```text
X-Agentscope-Admin-Token
```

错误 token：

```text
401 Unauthorized
```

课程默认不打开写操作。

## 15. 为什么管理端口最好独立

本课为了方便仍使用 18081。

真正生产建议：

```text
业务端口 18081
管理端口 18082
```

然后 K8s / NetworkPolicy / Ingress 做：

```text
18081 -> 用户流量
18082 -> 仅集群内部 / SRE
```

第 27 课已经演示了独立 management 端口的思路。

## 16. 先制造一次 Agent 调用

本课使用本地 deterministic Model，不需要 API Key：

```bash
./mvnw -pl 33-AdminOpsControlPlane spring-boot:run
```

```bash
curl -X POST http://localhost:18081/api/admin-demo/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"admin-1",
    "message":"hello admin control plane"
  }'
```

然后访问 Admin endpoints。

## 17. 查看课程导览

```bash
curl http://localhost:18081/api/admin-demo/guide
```

这里把读控制面、危险控制操作和 `/v1/admin` Data Plane 分开列出来。

## 18. 自动化测试

```bash
./mvnw -pl 33-AdminOpsControlPlane test
```

测试不需要启动真实 Web Server，而是在 Spring Context 中验证：

```text
AgentscopeStatusEndpoint
AgentscopeAgentsEndpoint
AgentscopeToolsEndpoint
AgentscopeModelsEndpoint
AgentscopeUsageEndpoint
```

都已经由 Starter 注册。

并验证：

```text
admin enabled = true
write enabled = false
Agent inventory 非空
Toolkit inventory 包含 system_info
Model inventory 包含 lesson-admin-demo-model
usage 有 global/by_agent/by_model
```

## 19. 生产部署建议

```text
Internet
   |
   v
Business Gateway
   |
   +----> Agent Business API

Private Network / VPN / Bastion
   |
   v
Admin Gateway
   |
   +----> /actuator/agentscope-*
   +----> /v1/admin/*
```

生产控制面至少考虑：

```text
网络隔离
身份认证
RBAC
Admin Token
审计日志
写操作二次确认
速率限制
敏感字段脱敏
```

## 20. 到这里的架构

```text
                   Agent Platform
                        |
          +-------------+-------------+
          |                           |
          v                           v
      Data Plane                  Control Plane
    User / Channel             Admin / SRE / Ops
          |                           |
          v                           v
    Harness/ReActAgent       Admin Starter/Actuator
          |                           |
          +-------------+-------------+
                        |
                        v
                 State / Model / Tool
```

## 21. 与后续关系

下一阶段协议层：

```text
34-AGUIProtocol
35-A2AProtocol
```

33 之后，我们已经不仅会“写 Agent”，也开始会“运维 Agent”。
