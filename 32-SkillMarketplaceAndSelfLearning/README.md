# 32-SkillMarketplaceAndSelfLearning

第 18 课学的是“怎么使用 Skill”。这一课继续解决两个更像平台的问题：

1. Skill 从哪里分发给 Agent？
2. Agent 能不能把自己总结出的经验变成新 Skill，并经过治理后正式生效？

## 1. 心智模型

```text
                Skill Sources
        +-----------+-----------+
        |           |           |
        v           v           v
      Git         MySQL       Nacos
        \           |           /
         \          |          /
          +---- Marketplace ---+
                   |
                   v
Project Global -> merged skills <- Workspace
                   ^
                   |
              Per-user skills
                   |
                   v
              HarnessAgent
```

同名 Skill 的优先级从低到高：

```text
projectGlobalSkillsDir
        < marketplace repositories
        < workspace/skills
        < <userId>/skills
```

## 2. 第 18 课还缺什么

第 18 课只是：

```text
开发者写 SKILL.md
      -> 放 workspace/skills
      -> Agent 加载
```

第 32 课要变成：

```text
Agent 在任务中发现可复用方法
           |
           v
      propose_skill
           |
           v
skills/_drafts/<name>/SKILL.md
           |
       Security Scan
           |
           v
      Promotion Gate
       /          \
    Approve       Reject/Defer
      |               |
      v               v
skills/<name>       保持草稿
      |
      v
使用次数 / Audit
      |
      v
Skill Curator
      |
   stale/archive
```

## 3. 默认为什么不 auto promote

`SkillManageConfig.defaults()` 的关键默认值：

```text
autoPromote = false
securityScan = true
draftsDir = skills/_drafts
mainDir = skills
```

这非常重要。

如果 Agent 一总结出一个 Skill 就立即进入正式环境，相当于：

> Agent 可以未经审核永久修改自己的行为规则。

生产中通常不应该这样做。

## 4. 本课 Builder

```java
HarnessAgent.builder()
    .workspace(Path.of(".agentscope/workspace"))
    .enableSkillManageTool(SkillManageConfig.defaults())
    .enableSkillPromotionGate(approvalGate, visibilityFilter)
    .environment("lesson")
    .enableSkillCurator(...)
    .build();
```

启用 `enableSkillManageTool` 后会得到：

```text
propose_skill
skill_manage
```

## 5. propose_skill

本课不是自己造一个 HTTP 写文件接口，而是 Controller 直接调用 Harness 真正注册的：

```text
propose_skill
```

它接收：

```json
{
  "name": "incident-summary",
  "description": "当需要总结线上事故时使用",
  "body": "# Incident Summary ...",
  "scripts": []
}
```

内部会自动生成 YAML frontmatter，并复用 `skill_manage` 的：

```text
校验
安全扫描
草稿 staging
审计
```

## 6. Promotion Gate

Gate 的职责不是“移动文件”这么简单，而是决定：

```text
这个 Agent 自己生成的能力，能不能进入正式技能集？
```

`SkillPromotionGate` 可以返回：

```text
Approve
Reject
Defer
```

本课为了可重复测试，使用自动 Approve 的 `lesson-reviewer`。

真实生产可以换成：

```text
LocalApprovalGate
NotifyAndWaitGate
企业审批系统
PR 审核
内部工作流
```

## 7. Visibility Filter

即使 Skill 已经被创建，也可以控制：

> 本次调用到底让不让模型看到它？

标准思路包括：

```text
EnvironmentFilter
CanaryFilter
AllowListFilter
CompositeFilter
```

于是可以做：

```text
Skill 已正式发布
     |
     +-- prod 10% 用户可见
     +-- staging 全量可见
     +-- 某些用户白名单可见
```

## 8. Skill Curator

长期运行之后会出现：

```text
skill 越来越多
旧 skill 不再使用
多个 skill 内容重复
上下文越来越吵
```

`SkillCuratorConfig` 提供：

```text
intervalHours
minIdleHours
staleAfterDays
archiveAfterDays
umbrellaPassMode
backupRetention
```

本课把 LLM umbrella pass 关闭：

```java
.umbrellaPassMode(DISABLED)
```

这样 `/curate` 可用于观察基础生命周期治理，而不会依赖额外模型判断。

## 9. 接口

### 提交草稿

```bash
curl -X POST http://localhost:18081/api/skills/propose \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"skill-lesson",
    "name":"incident-summary",
    "description":"当需要总结线上事故时使用",
    "body":"# Incident Summary\n\n1. 收集时间线\n2. 提炼根因\n3. 输出改进项",
    "scripts":[]
  }'
```

观察：

```text
.agentscope/workspace/skills/_drafts/incident-summary/SKILL.md
```

### 审核并晋升

```bash
curl -X POST http://localhost:18081/api/skills/promote \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"skill-lesson",
    "name":"incident-summary",
    "reviewerId":"nick"
  }'
```

晋升后：

```text
skills/incident-summary/SKILL.md
```

### 运行 Curator

```bash
curl -X POST http://localhost:18081/api/skills/curate
```

### 查看 Audit

```bash
curl http://localhost:18081/api/skills/audit
```

### 查看当前能力

```bash
curl 'http://localhost:18081/api/skills/inspect?userId=alice&sessionId=skill-lesson'
```

## 10. Marketplace 和 Workspace 的区别

Marketplace 更适合：

```text
团队级
平台级
跨项目共享
集中发布
版本治理
```

Workspace 更适合：

```text
当前项目专属
当前用户专属
运行时学习出的 Skill
```

官方 2.0.1 可扩展到：

```text
Git repository
Nacos
MySQL
PostgreSQL
Classpath
Custom AgentSkillRepository
```

## 11. 四层覆盖

```text
Layer 1: project global
Layer 2: marketplace
Layer 3: workspace shared
Layer 4: per-user workspace
```

同名时：

```text
Layer 4 > Layer 3 > Layer 2 > Layer 1
```

这让企业可以做到：

```text
公司通用 Skill
   ↓
团队覆盖版
   ↓
项目覆盖版
   ↓
用户个性化版
```

## 12. 自动化测试

```bash
./mvnw -pl 32-SkillMarketplaceAndSelfLearning test
```

测试直接调用真实 `propose_skill` Tool，不调用 LLM：

```text
propose_skill
   -> skills/_drafts/lesson-note/SKILL.md

promoteSkill
   -> SkillPromotionGate.Approve
   -> skills/lesson-note/SKILL.md
   -> draft 删除
```

## 13. 安全边界

自学习最危险的误解是：

> Agent 越能自动修改自己越高级。

企业里更合理的是：

```text
Agent 提议
   ↓
静态扫描
   ↓
审核
   ↓
灰度
   ↓
正式
   ↓
Usage + Audit
   ↓
淘汰
```

也就是：

> 自学习不是绕开治理，而是把“经验沉淀”也纳入治理。

## 14. 与后续关系

```text
18-Skills
  = 使用 Skill

32-SkillMarketplaceAndSelfLearning
  = 发布、审核、灰度、沉淀、淘汰 Skill

33-AdminOpsControlPlane
  = 从运维/管理面观察和控制 Agent 服务
```
