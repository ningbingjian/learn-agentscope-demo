# 16-SubAgentOrchestration

本节正式进入 Harness 的多 Agent 编排能力：**主 Agent 不再独自承担所有上下文，而是把适合独立处理的任务委派给子 Agent，再汇总结果。**

上一阶段我们已经学过状态、Workspace、Memory、Compaction 和 RAG。现在的问题变成：

> 一个复杂任务能不能拆成几个互相独立的专家任务，让多个 Agent 分工完成？

## 学习目标

完成本节后，你应该能理解：

- SubAgent 与普通 Tool 的区别。
- 主 Agent 为什么需要把重上下文任务委派出去。
- 工作区 `subagents/*.md` 如何声明子 Agent。
- `agent_spawn` 的作用。
- 同步子 Agent 与后台子 Agent 的差异。
- `task_id`、`task_output`、`wait_async_results`、`task_cancel` 分别负责什么。
- `WorkspaceMode.ISOLATED` 与 `SHARED` 的差异。
- `persistSession` 为什么会影响子 Agent 的会话复用。
- 为什么子 Agent 的结果只是主 Agent 的输入，而不是最终答案。

---

## 一、先建立正确心智模型

### Tool 是函数，SubAgent 是专家执行单元

Tool 更像：

```text
calculate(a, b)
read_file(path)
refund(orderId)
```

它通常做一个相对确定的原子动作。

SubAgent 更像：

```text
Researcher
  ├── 自己有 prompt
  ├── 自己有上下文
  ├── 自己可以推理多轮
  ├── 自己可以调用工具
  └── 最后把结果交回父 Agent
```

因此：

```text
Tool     = 函数
SubAgent = 临时专家员工
Main Agent = 调度者 / 负责人
```

### 为什么不能什么都放在主 Agent

假设用户说：

```text
请分析一个技术方案：
1. 调研现有方案
2. 找风险
3. 给改进建议
4. 最后做综合结论
```

如果全塞给主 Agent：

```text
Main Agent
 ├── 调研上下文
 ├── 评审上下文
 ├── 大量工具结果
 ├── 中间推理
 └── 最终总结
```

主上下文会迅速膨胀。

SubAgent 的思路是：

```text
                 Main Agent
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
     Researcher              Reviewer
     调研问题                 找风险
          │                     │
          └──────────┬──────────┘
                     ▼
                 Main Agent
                 统一汇总
```

父 Agent 只需要保留委派任务和子 Agent 的最终结果，不必把每个专家内部完整推理都塞回主上下文。

---

## 二、本案例的目录

```text
16-SubAgentOrchestration/
├── .agentscope/
│   └── workspace/
│       ├── AGENTS.md
│       └── subagents/
│           ├── researcher.md
│           └── reviewer.md
├── src/main/java/.../
│   ├── config/AgentConfiguration.java
│   └── web/SubAgentController.java
└── src/test/java/.../
    └── SubagentDeclarationContractTest.java
```

这里最重要的是：

```text
workspace/subagents/<agent-id>.md
```

**文件名就是 `agent_id`。**

本案例：

```text
researcher.md → agent_id = researcher
reviewer.md   → agent_id = reviewer
```

---

## 三、一步步编码

### 第 1 步：准备主 Agent 的 AGENTS.md

主 Agent 的职责不是“自己做完一切”，而是调度：

```markdown
- 调研任务优先交给 researcher
- 评审任务优先交给 reviewer
- 相互独立时可以并行委派
- 最终由主 Agent 汇总
```

这一步在告诉模型：**什么时候应该考虑委派。**

### 第 2 步：声明 researcher

`subagents/researcher.md`：

```markdown
---
description: 技术调研专家。适合做概念梳理、背景研究、方案对比和事实摘要。
steps: 6
---

你是 Researcher 子 Agent。
...
```

其中：

```text
description
```

非常关键。主 Agent 会根据 description 判断“这个子 Agent 适不适合当前任务”。

### 第 3 步：声明 reviewer

同理：

```markdown
---
description: 技术评审专家。适合审查设计、找风险、检查遗漏、提出反例和改进建议。
steps: 6
---
```

这样主 Agent 会看到两个不同职责的专家。

### 第 4 步：创建 HarnessAgent

```java
return HarnessAgent.builder()
        .name("orchestrator-agent")
        .model(model)
        .workspace(Paths.get(".agentscope/workspace"))
        .disableMemoryHooks()
        .disableCompaction()
        .build();
```

本节主动关闭 Memory 和 Compaction，原因不是它们不好，而是为了让实验只关注：

```text
主 Agent
  ↓
SubAgent
  ↓
结果汇总
```

### 第 5 步：暴露一个普通 chat 接口

```java
Msg reply = agent.call(
        List.of(new UserMessage(request.message())),
        context
).block();
```

Controller 并不直接写：

```java
researcher.call(...)
reviewer.call(...)
```

而是让 **主 Agent 自己决定是否使用 `agent_spawn`**。

这是重点。

应用层只给主 Agent 一个任务；委派属于 Agent 的推理决策。

---

## 四、运行案例

启动：

```bash
export DASHSCOPE_API_KEY="你的 Key"
./mvnw -pl 16-SubAgentOrchestration spring-boot:run
```

查看子 Agent 声明：

```bash
curl 'http://localhost:18081/api/subagents/specs?userId=alice&sessionId=s1'
```

调用：

```bash
curl -X POST http://localhost:18081/api/subagents/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"s1",
    "message":"请让 researcher 调研 SubAgent 的价值，让 reviewer 找这个架构的风险，最后你汇总。"
  }'
```

如果模型正确采用委派策略，会形成类似：

```text
User
 ↓
Main Agent
 ↓
agent_spawn(researcher)
 ↓
Researcher result
 ↓
agent_spawn(reviewer)
 ↓
Reviewer result
 ↓
Main Agent summary
```

如果两个任务在同一 reasoning 中同时发起，而且没有依赖关系，框架可以并行执行对应工具调用。

---

## 五、同步与后台 SubAgent

`agent_spawn` 不只是“调用另一个 Agent”。

### 同步

```text
timeout_seconds > 0
```

主 Agent 等子任务结果：

```text
Main Agent
   ↓
spawn
   ↓
等待
   ↓
SubAgent 完成
   ↓
结果作为 tool result
   ↓
主 Agent 继续 reasoning
```

适合：

- 当前下一步必须依赖结果。
- 任务较短。
- 结果位于关键路径。

### 后台

```text
timeout_seconds = 0
```

会立即得到：

```text
task_id
```

主 Agent 可以继续做别的事。

后台任务相关工具：

```text
agent_spawn        创建子 Agent / 发起任务
agent_send         给已有子 Agent 补消息
agent_list         查看子 Agent 实例

task_output        查看某个后台任务
task_list          查看后台任务列表
wait_async_results 等待一组后台任务
task_cancel        取消后台任务
```

不要混淆：

```text
agent_*  → 管 Agent 实例
task_*   → 管异步任务结果
```

---

## 六、ISOLATED 与 SHARED

### ISOLATED

默认模式。

```text
Parent Workspace
        │
        ├── SubAgent A Workspace
        └── SubAgent B Workspace
```

每个子 Agent 有自己的运行空间。

适合：

- 上下文隔离。
- 不希望不同专家互相污染文件。
- 专家有独立 Memory / Skills / Knowledge。

### SHARED

```text
Parent Agent
SubAgent A
SubAgent B
      ↓
共享同一个 Workspace
```

适合：

- 子 Agent 要直接修改同一批项目文件。
- 父 Agent 需要马上读取子 Agent 产物。

但共享意味着冲突风险也更大。

---

## 七、persistSession

默认 spawn 可以理解为：

```text
这次创建
→ 完成
→ 下次重新创建新的子会话
```

开启：

```java
.persistSession(true)
```

框架会基于：

```text
parentSessionId + agentId + label
```

得到稳定的子 Agent key。

于是可以：

```text
父会话第 1 轮
  ↓
researcher 记住任务背景

父会话第 2 轮
  ↓
再次 spawn 同一个 researcher
  ↓
继续上一次上下文
```

这适合“长期跟进的专家”。

---

## 八、为什么要做 SubagentDeclarationContractTest

虽然本案例主要用 Markdown spec，但测试里额外演示编程式声明：

```java
SubagentDeclaration.builder()
        .name("researcher")
        .description("技术调研专家")
        .inlineAgentsBody("...")
        .workspaceMode(WorkspaceMode.ISOLATED)
        .steps(6)
        .persistSession(true)
        .tools(List.of("read_file", "grep_files"))
        .build();
```

这样你能同时理解两种声明方式：

```text
Markdown spec
    → 项目内、可版本控制、适合团队维护

Java declaration
    → 动态配置、远程 Agent、运行时参数
```

运行测试：

```bash
./mvnw -pl 16-SubAgentOrchestration test
```

---

## 九、常见误区

### 误区 1：SubAgent 就是另一个 Tool

不是。

Tool 通常是确定性动作；SubAgent 有自己的推理循环和上下文。

### 误区 2：拆得越细越好

也不是。

如果任务只有一句话、没有独立上下文，spawn 一个子 Agent 的额外模型调用反而增加成本和延迟。

### 误区 3：主 Agent 不需要负责结果

错误。

SubAgent 的输出仍然应该由父 Agent 判断、合并和取舍。

### 误区 4：Plan Mode 会自动传给 SubAgent

在 AgentScope Java 2.0.1 中，Plan Mode 的只读约束不会自动继承给子 Agent。后面的第 17 课会专门解释。

---

## 十、本节边界

本节先学：

```text
声明 → 选择 → spawn → 结果返回 → 汇总
```

暂时不深入：

- 远程 SubAgent。
- Channel 暴露子 Agent 给用户。
- 跨节点后台任务。
- 复杂任务 DAG。

这些后面再进阶。

下一节：

```text
17-PlanMode
```

解决的问题是：

> Agent 面对复杂任务时，怎么先只读分析和写计划，再经过确认进入执行阶段？
