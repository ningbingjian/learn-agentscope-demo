# 17-PlanMode

本节学习 AgentScope Harness 的 **Plan Mode（计划模式）**：让 Agent 在真正动手之前先进入只读阶段，调查上下文、写出计划，再经过退出流程进入执行阶段。

核心思想只有一句话：

> 复杂任务不要边想边改；先把意图、范围、风险和步骤想清楚，再执行。

## 学习目标

完成本节后，你应该能理解：

- Plan Mode 解决什么问题。
- `enablePlanMode()` 做了什么。
- `plan_enter / plan_write / plan_exit` 三个工具各自负责什么。
- 为什么 Plan Mode 默认限制写操作。
- 为什么 `plan_exit` 与 HITL / Permission System 有关系。
- Plan Mode 状态为什么属于 session 状态。
- 如何通过业务代码 `enterPlanMode / exitPlanMode / isPlanModeActive` 主动控制。
- `PLAN.md` 与 `AgentState` 的区别。
- Plan Mode 与 `todo_write`、SubAgent 的关系。

---

## 一、为什么要 Plan Mode

没有 Plan Mode 的复杂任务可能是：

```text
用户：重构订单模块
       ↓
Agent 看了两个文件
       ↓
马上 edit_file
       ↓
发现依赖关系
       ↓
再改第三个文件
       ↓
发现方向不对
       ↓
继续补丁
```

这就是典型的：

```text
边想
边改
边发现问题
```

复杂工程任务中风险很高。

Plan Mode 把流程变成：

```text
需求
 ↓
Plan Mode
 ↓
只读调查
 ↓
写 PLAN.md
 ↓
确认退出 Plan Mode
 ↓
执行阶段
 ↓
真正修改
```

它实际上给 Agent 增加了一个**阶段状态机**。

---

## 二、Plan Mode 的状态机

```text
BUILD MODE
    │
    │ plan_enter
    ▼
PLAN MODE
    │
    ├── read_file     ✅
    ├── grep_files    ✅
    ├── plan_write    ✅
    ├── todo_write    ✅
    ├── write_file    ❌
    └── edit_file     ❌
    │
    │ plan_exit
    ▼
HITL / Permission ASK
    │
    │ approve
    ▼
BUILD MODE
```

这里最关键的是：

```text
Plan Mode != 一段 prompt
```

它是 Harness 中真实存在的运行状态和工具权限约束。

---

## 三、本案例如何配置

核心代码：

```java
return HarnessAgent.builder()
        .name("planner-agent")
        .model(model)
        .workspace(Paths.get(".agentscope/workspace"))
        .enablePlanMode()
        .planFileDirectory("plans")
        .disableMemoryHooks()
        .disableCompaction()
        .build();
```

重点：

```java
.enablePlanMode()
```

安装 Plan Mode 相关能力。

然后：

```java
.planFileDirectory("plans")
```

计划文件默认写到：

```text
workspace/plans/PLAN.md
```

本节主动关掉 Memory 和 Compaction，避免实验时出现其它 LLM 调用。

---

## 四、三个核心工具

### plan_enter

进入计划阶段。

```text
BUILD
 ↓
plan_enter
 ↓
PLAN
```

### plan_write

把计划写入专用计划文件。

它不是通用 `write_file`。

这是一个非常重要的安全设计：

```text
Plan Mode
   ↓
允许 plan_write
   ↓
只能写计划

而不是：

Plan Mode
   ↓
允许 write_file
   ↓
那就能写整个项目了 ❌
```

### plan_exit

表示 Agent 认为计划已经完成，希望进入执行阶段。

Agent 驱动的 `plan_exit` 会进入 HITL / permission ASK 流程，让用户确认。

这正好复用了第 08 课：

```text
08-PermissionHITL
        ↓
17-PlanMode exit approval
```

---

## 五、一步步编码案例

### 第 1 步：准备 AGENTS.md

我们告诉 Agent：

```markdown
- 复杂任务先规划
- Plan Mode 只调查，不修改
- 写 PLAN.md
- 计划包含风险、验证和回滚
```

### 第 2 步：开启 Plan Mode

```java
.enablePlanMode()
```

### 第 3 步：提供 chat 接口

```java
agent.call(
    List.of(new UserMessage(message)),
    RuntimeContext.builder()
        .userId(userId)
        .sessionId(sessionId)
        .build()
).block();
```

这里模型可以自主选择调用：

```text
plan_enter
plan_write
plan_exit
```

### 第 4 步：提供管理端 enter 接口

```java
agent.enterPlanMode(context);
```

这不是让 LLM 决定，而是业务代码主动切换。

适合：

```text
前端按钮：进入规划模式
管理后台：强制只读分析
工作流：规划阶段
```

### 第 5 步：提供 exit 接口

```java
agent.exitPlanMode(context);
```

注意：

> 程序化 `exitPlanMode()` 不会走 LLM 的 `plan_exit` HITL 流程。

它是管理 API / 应用层逃生口。

### 第 6 步：查看状态

```java
agent.isPlanModeActive(context)
```

并同时读取：

```text
plans/PLAN.md
```

于是 API 可以同时告诉你：

```json
{
  "planModeActive": true,
  "planPath": "plans/PLAN.md",
  "plan": "..."
}
```

---

## 六、启动和实验

```bash
export DASHSCOPE_API_KEY="你的 Key"
./mvnw -pl 17-PlanMode spring-boot:run
```

### 实验 1：程序化进入

```bash
curl -X POST 'http://localhost:18081/api/plan/enter?userId=alice&sessionId=s1'
```

然后查看：

```bash
curl 'http://localhost:18081/api/plan/state?userId=alice&sessionId=s1'
```

应看到：

```text
planModeActive = true
```

### 实验 2：让 Agent 写计划

```bash
curl -X POST http://localhost:18081/api/plan/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"s1",
    "message":"分析如何给 Spring Boot 订单模块增加退款功能，把完整计划写到 PLAN.md，不要执行真实修改。"
  }'
```

然后再次：

```bash
curl 'http://localhost:18081/api/plan/state?userId=alice&sessionId=s1'
```

观察 `plan` 内容。

### 实验 3：程序化退出

```bash
curl -X POST 'http://localhost:18081/api/plan/exit?userId=alice&sessionId=s1'
```

状态回到：

```text
planModeActive = false
```

---

## 七、Plan Mode 为什么是 per-session

假设：

```text
alice / session-1 → PLAN MODE
bob   / session-2 → BUILD MODE
```

二者应该互不影响。

这是因为 Plan Mode 状态属于：

```text
AgentState
  ↓
(userId, sessionId)
```

而不是一个全局 boolean。

测试里专门验证：

```text
enter alice/s1

alice/s1 → true
bob/s2   → false
```

因此单例 HarnessAgent 可以同时服务：

```text
正在规划的 session
正在执行的 session
普通聊天 session
```

---

## 八、PLAN.md 与 AgentState 的区别

Plan Mode 里存在两个不同东西。

### AgentState

保存：

```text
当前是不是 Plan Mode
```

它是运行状态。

### PLAN.md

保存：

```text
计划具体写了什么
```

它是 Workspace 文件。

所以：

```text
Plan Mode state → AgentState
Plan content    → Workspace/plans/PLAN.md
```

这再次呼应第 12 课：

```text
State != Workspace
```

---

## 九、和 todo_write 的区别

Plan：

```text
为什么做
怎么做
影响什么
有什么风险
怎么验证
```

Todo：

```text
现在具体执行到哪一步
```

典型组合：

```text
PLAN.md
  ↓
退出 Plan Mode
  ↓
todo_write
  ↓
1. 新建接口      completed
2. 修改 service   in_progress
3. 增加测试       pending
```

Plan 是设计文档，Todo 是执行清单。

---

## 十、和 SubAgent 的关系

上一课我们学习了 SubAgent。

很自然会想到：

```text
Plan Mode 中
  ↓
spawn Researcher
  ↓
帮我调研
```

但 AgentScope Java 2.0.1 有一个明确边界：

> Plan Mode 的只读约束不会自动继承给子 Agent。

因此如果你在 Plan 阶段使用 SubAgent，需要自己：

- 给子 Agent 只读 tool allowlist；或者
- 子 Agent 自己也启用 Plan Mode。

不能默认认为：

```text
父 Agent 只读
=
所有子 Agent 自动只读
```

---

## 十一、测试

```bash
./mvnw -pl 17-PlanMode test
```

`PlanModeStateTest` 不调用真实模型，而是直接验证：

```text
初始 false
 ↓
enterPlanMode(alice/s1)
 ↓
alice/s1 = true
bob/s2   = false
 ↓
exitPlanMode(alice/s1)
 ↓
false
```

这比只测试 Controller 更直接，因为它验证的就是 Harness Plan Mode 状态本身。

---

## 十二、常见误区

### 误区 1：Plan Mode 就是“提示模型先想一想”

不是。

它有真实运行状态、工具限制和计划文件。

### 误区 2：只看最终文本里有没有计划

不够。

模型可能只是说了一个计划，却没有真正调用 `plan_write`。

应该同时检查：

```text
isPlanModeActive
plans/PLAN.md
Tool Events
```

### 误区 3：默认允许 shell 就还是绝对只读

不对。

Plan Mode 默认不放开 shell。如果使用 `allowShellInPlanMode()`，只是靠提示约束 shell 做只读调查，安全保证会更弱。

### 误区 4：程序化 exit 也会弹 HITL

不会。

管理代码调用 `exitPlanMode()` 是直接切状态；模型调用 `plan_exit` 才走交互确认路径。

---

## 十三、本节边界

本节重点是：

```text
阶段控制 + 只读规划 + PLAN.md + HITL 出口
```

暂时不深入：

- Todo DAG。
- Admin Starter。
- 沙箱下的 Plan Mode。
- 多 Agent 规划继承。

下一节：

```text
18-Skills
```

解决：

> 一套已经验证过的工作方法，怎么沉淀成 Agent 以后能自动复用的能力包？
