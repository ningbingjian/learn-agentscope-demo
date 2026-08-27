# 43-EnterpriseChannels

本课把第 21 课的 `Channel + Gateway` 从浏览器聊天继续扩展到真实企业消息平台。

第 21 课学的是统一入口抽象；本课学的是：**飞书、钉钉、企业微信、GitHub、GitLab 的平台事件，怎样被适配成 AgentScope 可以消费的统一 Channel 消息。**

---

## 1. 本课目标

完成本课后应该能回答：

1. Channel 和 HTTP Controller 有什么区别？
2. DingTalk / Feishu / WeCom / GitHub / GitLab 的传输模式为什么不同？
3. 平台 webhook 重试为什么必须做幂等？
4. bot-to-bot 死循环为什么不能只靠平台限流？
5. `IdempotencyStore` 与 `BotLoopGuard` 分别解决什么问题？
6. 入站消息经过哪些步骤才能进入 Gateway？
7. Agent 的回复如何重新映射成平台原生消息？
8. 为什么签名校验、解密、token 获取应该由 Channel Adapter 负责？
9. 生产环境应该如何管理 AppSecret/WebhookSecret？

---

## 2. 官方 Channel Adapter

AgentScope Java 2.0.1 提供五类官方 Channel：

| 平台 | Artifact | 核心类 | 入站传输 |
| --- | --- | --- | --- |
| 钉钉 | `agentscope-extensions-channel-dingtalk` | `DingTalkChannel` | Stream / WebSocket |
| 飞书 | `agentscope-extensions-channel-feishu` | `FeishuChannel` | HTTP 事件回调 |
| 企业微信 | `agentscope-extensions-channel-wecom` | `WeComChannel` | 加密 HTTP 回调 |
| GitHub | `agentscope-extensions-channel-github` | `GitHubChannel` | Webhook |
| GitLab | `agentscope-extensions-channel-gitlab` | `GitLabChannel` | Webhook |

它们共享：

```text
agentscope-extensions-channel-common
├── IdempotencyStore
└── BotLoopGuard
```

---

## 3. 统一架构

虽然外部协议不同，但内部目标一致：

```text
Platform Event
    ↓
签名 / 解密 / 鉴权
    ↓
Inbound Mapper
    ↓
IdempotencyStore
    ↓
BotLoopGuard
    ↓
InboundMessage
    ↓
Channel
    ↓
Gateway
    ↓
HarnessAgent
    ↓
Agent reply
    ↓
Outbound Client
    ↓
Platform API
```

Agent 不应该知道自己来自飞书还是 GitHub。

这就是 Adapter 的价值。

---

## 4. 为什么 Channel 不是普通 Controller

普通 Controller 往往只解决：

```text
HTTP JSON → Java Method
```

企业 Channel 还必须解决：

```text
平台签名
事件格式
消息去重
机器人死循环
回复目标解析
token 生命周期
平台错误码
重试
```

因此生产系统不要把所有平台差异塞进 Agent Controller。

---

## 5. IdempotencyStore

Webhook 平台经常在超时或 5xx 后重复投递同一个事件。

例如：

```text
GitHub event id = e-100
  ↓
服务处理完成，但响应超时
  ↓
GitHub retry
  ↓
e-100 再来一次
```

如果没有幂等：

```text
Issue comment → Agent → 发布两条回复
```

AgentScope 2.0.1 的公共 Channel 模块提供：

```java
IdempotencyStore store = new IdempotencyStore();

boolean first = store.firstSeen("event-100");
```

第一次：

```text
true
```

TTL 内再次出现：

```text
false
```

默认实现是进程内、带 TTL 和容量上限的去重表。

> 多副本场景如果平台请求可能落到不同 Pod，应把幂等提升到 Redis/数据库等共享层，不能误以为 JVM 内 store 能解决跨 Pod 去重。

---

## 6. BotLoopGuard

另一个典型事故：

```text
Bot A 回复 Bot B
Bot B 回复 Bot A
Bot A 再回复 Bot B
...
```

或者脚本持续触发同一个 peer。

`BotLoopGuard` 按 peer 做滑动窗口限流：

```java
BotLoopGuard guard = new BotLoopGuard(
    20,
    60_000L,
    60_000L
);
```

含义：

```text
60 秒最多 20 个事件
超过 → peer 进入 60 秒 cooldown
```

本课测试把阈值缩小到 2，方便稳定验证：

```text
event 1 → allow
 event 2 → allow
 event 3 → reject + cooldown
```

---

## 7. 本课代码

`ChannelCatalogService` 做两件事。

### 7.1 展示五种真实 Adapter 类型

```java
DingTalkChannel.class
FeishuChannel.class
WeComChannel.class
GitHubChannel.class
GitLabChannel.class
```

这样可以确保 Maven 依赖和官方类型都真实存在。

### 7.2 演示入站公共保护层

```java
boolean firstSeen = idempotencyStore.firstSeen(messageId);
boolean withinLoopBudget = firstSeen && botLoopGuard.allow(peerKey);
```

这里没有伪造平台 token，也不会连接真实平台。

---

## 8. 五个平台的关键差异

### DingTalk

钉钉使用 Stream 模式：

```text
应用主动建立持久连接
    ↓
平台通过 WebSocket 推事件
```

优点是公网 webhook 配置压力较低。

### Feishu

飞书常见模式：

```text
Event Subscription
    ↓
HTTP callback
    ↓
verification/signature
```

### WeCom

企业微信回调重点是：

```text
signature verification
+ encrypted payload
+ decrypt/encrypt
```

### GitHub / GitLab

两者更偏 DevOps Agent：

```text
Issue
Pull Request / Merge Request
Comment
Webhook
```

特别适合：

```text
Code Review Agent
Issue Triage Agent
CI Failure Diagnosis Agent
```

---

## 9. Channel 与 Gateway 的关系

第 21 课：

```text
Browser → ChatUiChannel → Gateway → Agent
```

第 43 课：

```text
Feishu ─┐
DingTalk├─→ Channel Adapter → Gateway → Agent
WeCom   │
GitHub  │
GitLab ─┘
```

Gateway 负责统一路由，Channel 负责平台边界。

---

## 10. userId / sessionId 设计

企业平台通常给你：

```text
platform user id
chat id / conversation id
message id
```

建议映射：

```text
userId
= platform + tenant + platformUserId

sessionId
= platform + chatId/threadId
```

不要直接把昵称当用户主键。

昵称会变化，而且可能重复。

---

## 11. Secret 管理

不要：

```yaml
app-secret: abc123
webhook-secret: xyz456
```

直接提交 Git。

生产推荐：

```text
Kubernetes Secret
Vault
Cloud Secret Manager
```

并区分：

```text
App ID
App Secret
Verification Token
Encryption Key
Webhook Secret
Access Token
```

这些不是同一个东西。

---

## 12. 安全边界

Webhook Controller 至少需要：

```text
签名校验
时间戳窗口
事件幂等
body size limit
来源校验
日志脱敏
```

对 GitHub/GitLab 类 Channel，还要防止用户内容直接变成高权限 Tool 参数。

例如：

```text
Issue comment:
“删除生产数据库”
```

不能因为内容来自 GitHub 就自动获得生产权限。

Channel 认证 != Tool 授权。

---

## 13. 启动

```bash
./mvnw -pl 43-EnterpriseChannels spring-boot:run
```

查看五种 Provider：

```bash
curl http://localhost:18081/api/channels/providers
```

测试一次幂等保护：

```bash
curl -X POST 'http://localhost:18081/api/channels/guard?peer=alice&messageId=event-1'
```

同一个命令再执行一次：

```text
firstSeen: false
withinLoopBudget: false
```

---

## 14. 自动化测试

```bash
./mvnw -pl 43-EnterpriseChannels test
```

测试包含：

```text
5 个官方 Adapter 类在 classpath
IdempotencyStore 去重
BotLoopGuard cooldown
Service 组合保护逻辑
```

完全不需要真实平台账号。

---

## 15. 本课结论

真正的企业 Channel 不是：

```text
再写 5 个 Controller
```

而是：

```text
平台差异留在 Adapter
统一消息进入 Gateway
公共安全能力复用
Agent 逻辑保持不变
```

这才是多渠道 Agent 架构。
