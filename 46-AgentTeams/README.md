# 46-AgentTeams

本课深入 **Agent Teams / 多 Agent 团队协作**，同时严格遵守本项目的版本约束：**AgentScope Java 2.0.1**。

> 重要版本结论：`v2.0.1` 中没有 `TeamsMiddleware`、`TeamTool`、`TeamClient` 这一套官方 AgentTeams Runtime。它们出现在后续源码演进中。本课不会偷偷升级依赖，也不会伪造 2.0.1 API。

## 1. 为什么仍然保留这节课

AgentTeams 代表的不是一个简单 API，而是一种重要的多 Agent 协作模型：

```text
                   Team
                    │
              shared board
                    │
        ┌───────────┼───────────┐
        ↓           ↓           ↓
       lead        w1          w2
        │           │           │
        └────── mailbox ────────┘
```

与第 16 课 SubAgent 不同：

```text
SubAgent
父 Agent 主动委派 → 子 Agent → 返回父 Agent

Agent Team
多个成员围绕共享任务板协作、认领任务、发消息、并发推进
```

## 2. 2.0.1 的正确处理方式

本项目选择：

```text
不升级 AgentScope
不复制新版 AgentScope 源码
不假装 2.0.1 有 TeamsMiddleware

而是：
实现 application-layer Team Board
学习 AgentTeams 最核心的协调语义
```

启动后可直接检查版本边界：

```bash
curl http://localhost:18081/api/teams/version-boundary
```

会看到：

```json
{
  "lockedAgentScopeVersion": "2.0.1",
  "officialAgentTeamsAvailable": false,
  "officialTeamToolAvailable": false
}
```

## 3. Shared Task Board

任务结构：

```text
id
subject
state
owner
version
result
```

状态流：

```text
PENDING
   ↓ claim
IN_PROGRESS
   ↓ complete
COMPLETED
```

## 4. 为什么一定要 version / CAS

两个 worker 同时看到：

```text
task-1
state=PENDING
version=0
```

然后：

```text
Worker A claim(expectedVersion=0)
→ success
→ version=1

Worker B claim(expectedVersion=0)
→ conflict
```

否则同一个任务可能被两名 Agent 同时执行。

这与前面第 38 课 `JdbcStore.putIfVersion()` 的思想完全相同：**共享协作状态必须考虑 lost update**。

## 5. Mailbox

团队协作还需要 Agent 之间的异步消息：

```text
worker-a → lead : task finished
lead     → worker-b : please verify
lead     → * : stop current work
```

本课提供简单 mailbox：

```java
sendMessage(from, to, content)
messagesFor(member)
```

真正的生产实现应该使用持久化消息总线或后续第 47 课的 `MessageBus` 思路，而不是只保存在 JVM List 中。

## 6. API 实验

启动：

```bash
./mvnw -pl 46-AgentTeams spring-boot:run
```

创建任务：

```bash
curl -X POST http://localhost:18081/api/teams/tasks \
  -H 'Content-Type: application/json' \
  -d '{"subject":"review permission design"}'
```

假设返回 `id=abc123`、`version=0`，worker-a 认领：

```bash
curl -X POST http://localhost:18081/api/teams/claim \
  -H 'Content-Type: application/json' \
  -d '{"taskId":"abc123","member":"worker-a","expectedVersion":0}'
```

worker-b 如果仍拿旧 version=0 再 claim，会收到冲突。

完成任务：

```bash
curl -X POST http://localhost:18081/api/teams/complete \
  -H 'Content-Type: application/json' \
  -d '{"taskId":"abc123","member":"worker-a","expectedVersion":1,"result":"done"}'
```

## 7. 与未来官方 AgentTeams 的映射

后续版本中的官方 Teams Runtime 可以理解成把本课这些 application-layer 概念框架化：

```text
本课 TeamBoard       → 官方 shared board / TeamTask
本课 expectedVersion → 官方 optimistic coordination
本课 mailbox         → 官方 TeamMessage / wakeup
本课 member          → 官方 team role/member
```

因此本课不是“自己造轮子替代官方实现”，而是明确版本边界后学习底层协调模型。

## 8. 自动化测试

```bash
./mvnw -pl 46-AgentTeams test
```

测试验证两件事：

1. 2.0.1 classpath 中确实没有 `TeamsMiddleware / TeamTool`。
2. application Team Board 的 CAS 与 mailbox 行为正确。

## 9. 本课结论

最重要的知识不是类名，而是：

```text
Multi-Agent Team
=
shared task board
+ member ownership
+ optimistic concurrency
+ mailbox
+ wakeup
+ lifecycle governance
```

其中 `wakeup` 会在下一课通过 AgentScope 2.0.1 的真实 `MessageBus + AsyncToolMiddleware` 继续深入。
