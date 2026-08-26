# 18-Skills

本节学习 AgentScope Harness 的 **Skill（技能）**：把一套已经验证过的工作方法、SOP、检查清单和参考资料沉淀成一个标准能力包，让 Agent 在合适的任务里自动发现并按需加载。

这一节需要先区分三个概念：

```text
Tool     = 一个动作 / 函数
Skill    = 一套可复用工作方法
SubAgent = 一个有自己上下文的专家执行单元
```

## 学习目标

完成本节后，你应该能理解：

- Skill 为什么不是 Prompt 的简单别名。
- `SKILL.md` 的标准结构。
- `name` 和 `description` 为什么必须写在 YAML frontmatter。
- `workspace/skills/` 为什么不需要额外注册就能生效。
- Agent 为什么先看到 skill 元数据，再按需加载详情。
- `load_skill_through_path` 的作用。
- references / scripts 等资源如何与 SKILL.md 配合。
- Skill 与 Tool、SubAgent、Plan Mode 的关系。
- workspace shared skill 与 user-scoped skill 的优先级。
- Skill marketplace / repository 是怎么扩展的。

---

## 一、Skill 解决什么问题

假设团队经常做 Java Code Review。

如果每次都在用户 prompt 里写：

```text
请检查 Controller 职责
请检查事务
请检查 SQL
请检查 N+1
请检查异常
请按 Blocker/Major/Minor 输出
...
```

这套规则会不断重复。

而且不同开发者会写出不同版本。

Skill 的思路：

```text
一次沉淀
  ↓
skills/java-code-review/SKILL.md
  ↓
以后 Agent 自动发现
  ↓
需要时加载
  ↓
按统一流程执行
```

它把“团队经验”变成版本化资产。

---

## 二、Skill 目录结构

本案例提供两个 skill：

```text
workspace/
└── skills/
    ├── java-code-review/
    │   ├── SKILL.md
    │   └── references/
    │       └── checklist.md
    │
    └── api-design/
        └── SKILL.md
```

一个标准 Skill 最少只有：

```text
<skill-name>/
└── SKILL.md
```

也可以附加：

```text
references/
scripts/
assets/
examples/
```

这些资源不会全部无脑塞进模型上下文，而是由 Agent 按需读取。

---

## 三、SKILL.md 的格式

```markdown
---
name: java-code-review
description: 当用户需要评审 Java、Spring Boot、接口实现、服务层代码或 PR 设计时使用。
---

# Java Code Review

执行步骤：
1. 读取 checklist
2. 判断职责层
3. 检查 correctness
4. 检查 architecture
5. 按严重级别输出
```

### name

唯一技能名称。

### description

非常重要。

Agent 首先根据 description 判断：

```text
当前任务
  ↓
哪些 skill 可能适合？
```

如果 description 写得太宽泛：

```text
description: 一个有用的技能
```

模型根本不知道什么时候该选它。

应该写清触发场景：

```text
当用户需要评审 Java、Spring Boot、接口实现、服务层代码或 PR 设计时使用。
```

### 正文

正文不是给用户看的帮助文档，而是：

> 给 Agent 执行这项能力时看的 SOP。

因此应该偏：

```text
步骤
判断条件
约束
输出要求
错误处理
```

---

## 四、Agent 怎么发现 Skill

每轮推理时，Harness 会向 Agent 暴露一个类似：

```xml
<available_skills>
  <skill>
    <name>java-code-review</name>
    <description>...</description>
    <skill-id>...</skill-id>
  </skill>
  <skill>
    <name>api-design</name>
    <description>...</description>
  </skill>
</available_skills>
```

注意这里**不是直接把整个 SKILL.md 全塞进去**。

否则 100 个 Skill 会把上下文撑爆。

实际流程是：

```text
Step 1
只看 name + description
       ↓
判断是否匹配
       ↓
Step 2
load_skill_through_path(..., "SKILL.md")
       ↓
读完整指令
       ↓
Step 3
如果需要 reference
       ↓
再 load_skill_through_path(..., "references/xxx.md")
```

这就是按需加载。

---

## 五、本案例为什么不注册 SkillRepository

配置只有：

```java
HarnessAgent.builder()
        .workspace(Paths.get(".agentscope/workspace"))
        .build();
```

并没有：

```java
.skillRepository(...)
```

因为：

```text
workspace/skills/
```

是 Harness 的内置技能来源。

把目录放进去就会被发现。

这非常适合：

- 项目级 SOP。
- 团队编码规范。
- 当前 repo 特有工作流。
- 和代码一起版本控制的技能。

---

## 六、一步步实现 java-code-review Skill

### 第 1 步：建目录

```text
skills/java-code-review/
```

### 第 2 步：写 SKILL.md

把“什么时候用”和“怎么做”分开：

```yaml
name: java-code-review
description: 当用户需要评审 Java ... 时使用。
```

正文：

```text
读取 checklist
 ↓
识别 Controller / Service / Repository
 ↓
先 correctness
 ↓
再 architecture
 ↓
按 BLOCKER / MAJOR / MINOR
 ↓
给 merge 结论
```

### 第 3 步：把长规则移到 references

如果所有检查项都塞在 SKILL.md：

```text
Skill 元数据
+ SOP
+ 100 条详细规范
+ 各种案例
```

会越来越重。

所以拆成：

```text
SKILL.md
  ↓
references/checklist.md
```

只有真正执行 code review 时才加载 checklist。

### 第 4 步：再做一个 api-design Skill

这是为了观察**技能选择**。

用户说：

```text
review Spring Service
```

应该更接近：

```text
java-code-review
```

用户说：

```text
设计退款 REST API
```

应该更接近：

```text
api-design
```

而不是每个任务都加载全部 Skill。

---

## 七、运行案例

```bash
export DASHSCOPE_API_KEY="你的 Key"
./mvnw -pl 18-Skills spring-boot:run
```

查看当前 Skill Catalog：

```bash
curl http://localhost:18081/api/skills/catalog
```

会看到类似：

```json
[
  {
    "name":"api-design",
    "description":"...",
    "skillId":"api-design_workspace-demo"
  },
  {
    "name":"java-code-review",
    "description":"...",
    "skillId":"java-code-review_workspace-demo"
  }
]
```

注意这个 catalog endpoint 是学习辅助接口：它直接使用 `SkillUtil` 解析本地 SKILL.md，方便你观察格式。

真正 Harness Agent 的技能发现由 Harness 自己完成。

### Code Review 实验

```bash
curl -X POST http://localhost:18081/api/skills/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"s1",
    "message":"Review 一个 Spring Controller：直接写 SQL，没有参数校验，异常全是 500。"
  }'
```

理想过程：

```text
Agent 看 available_skills
 ↓
命中 java-code-review
 ↓
加载 SKILL.md
 ↓
发现 references/checklist.md
 ↓
加载 checklist
 ↓
按照 BLOCKER / MAJOR / MINOR 评审
```

### API Design 实验

```bash
curl -X POST http://localhost:18081/api/skills/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"s2",
    "message":"设计一个订单退款 REST API，需要考虑幂等、错误码和重复提交。"
  }'
```

此时应该优先选择：

```text
api-design
```

---

## 八、Skill 和 Tool 的区别

### Tool

```java
@Tool
String refund(String orderId) { ... }
```

是一个动作。

### Skill

```text
如何处理退款需求：
1. 校验订单状态
2. 检查幂等
3. 判断退款渠道
4. 执行退款
5. 验证结果
6. 记录审计
```

是一套“如何做事”的知识。

所以：

```text
Skill 决定 HOW
Tool 提供 DO
```

Agent 可能：

```text
加载 refund Skill
 ↓
根据流程判断下一步
 ↓
调用 query_order Tool
 ↓
调用 refund Tool
 ↓
调用 audit Tool
```

---

## 九、Skill 和 SubAgent 的区别

Skill 没有独立上下文。

它被加载进当前 Agent：

```text
Main Agent
  + Skill instructions
```

SubAgent 是：

```text
Main Agent
  ↓
创建另一个 Agent
  ↓
独立上下文 / 推理循环
```

判断方法：

### 用 Skill

如果只是：

```text
“教当前 Agent 用标准流程做事”
```

### 用 SubAgent

如果需要：

```text
独立上下文
大量中间信息
并行任务
专家长期会话
```

---

## 十、Skill 和 Plan Mode 的组合

这三课其实可以串起来：

```text
用户复杂需求
     ↓
Main Agent
     ↓
加载 architecture-design Skill
     ↓
Plan Mode
     ↓
写 PLAN.md
     ↓
SubAgent 调研 / review
     ↓
退出 plan
     ↓
执行
```

所以：

```text
Skill    = 方法论
Plan     = 当前任务方案
SubAgent = 分工执行者
```

---

## 十一、Skill 的四层来源

Harness 2.x 可以同时合成多个来源。

从低到高大致是：

```text
项目全局目录
   ↓
Skill Repository / Marketplace
   ↓
workspace/skills
   ↓
<userId>/skills
```

同名时，高优先级覆盖低优先级。

这意味着你可以做到：

```text
公司通用 code-review
   ↓
当前项目覆盖一版
   ↓
Alice 又有自己的实验版
```

不同用户可以看到不同 Skill。

---

## 十二、Skill Marketplace

当 Skill 不想放在项目 repo 里，可以接：

```java
.skillRepository(...)
```

可扩展后端包括：

```text
Git
Classpath
Nacos
MySQL
自定义 AgentSkillRepository
```

这就从：

```text
repo 内技能
```

升级成：

```text
企业内部技能市场
```

非常适合统一发布：

- Java Review Skill
- SQL Review Skill
- 故障排查 Skill
- 发布检查 Skill
- 广告投放诊断 Skill
- 数据分析 Skill

---

## 十三、自学习 Skill

Harness 还支持更进一步的：

```text
Agent 使用过程中
  ↓
发现一套成熟流程
  ↓
propose_skill
  ↓
skills/_drafts/
  ↓
人工审核
  ↓
正式 skill
```

相关 builder：

```text
enableSkillManageTool(...)
enableSkillPromotionGate(...)
enableSkillCurator(...)
```

但本节先不打开。

原因：

> 先学会“正确使用别人写好的 Skill”，再学“让 Agent 自己创造 Skill”。

否则一次塞太多概念。

---

## 十四、测试

```bash
./mvnw -pl 18-Skills test
```

`SkillFormatTest` 直接调用 AgentScope Core 的：

```java
SkillUtil.createFrom(markdown, null, "test")
```

验证：

```text
YAML frontmatter
  ↓
name
  ↓
description
  ↓
正文 skillContent
  ↓
AgentSkill
```

同时验证缺少必须 metadata 时抛异常。

这说明 `SKILL.md` 不是“随便写个 Markdown 文件”，而是有明确结构契约。

---

## 十五、常见误区

### 误区 1：Skill 越长越好

错误。

Skill 应该保留核心工作流程，长资料拆到 references 按需加载。

### 误区 2：把所有内部知识都做成 Skill

不合适。

静态领域事实更接近 Knowledge / RAG；工作方法、SOP 更适合 Skill。

### 误区 3：Skill 会自己执行 Java 方法

不会。

Skill 是指令和资源。真正执行动作仍依赖 Tool / Shell / SubAgent。

### 误区 4：每轮把所有 Skill 全文放进 prompt

Harness 不是这样做。

先暴露最少元数据，再按需加载详情。

---

## 十六、这一阶段总结

第 16～18 课可以放在一张图里：

```text
                 HarnessAgent
                      │
       ┌──────────────┼──────────────┐
       ▼              ▼              ▼
     Skill          Plan Mode      SubAgent
       │              │              │
   标准方法        当前任务计划      专家分工
       │              │              │
       └──────────────┼──────────────┘
                      ▼
                  Tool / Action
```

现在你已经从“会写一个 Agent”进入：

> 如何组织一个长期运行、可规划、可分工、可沉淀能力的 Agent 系统。

后续建议进入：

```text
19-MCPIntegration
20-ToolConfiguration
21-GatewayAndChannel
```
