# 12-HarnessWorkspace

本节正式学习 AgentScope Harness 的 **Workspace**。

前面的 Harness 案例已经写过：

```java
.workspace(Paths.get(".agentscope/workspace"))
```

但之前 Workspace 只是配置参数。本节要真正回答：

> 这个目录里可以放什么？Harness 如何读取？它和 AgentState 到底是什么关系？

## 学习目标

完成本节后，你应该能够理解：

- Workspace 为什么是 Harness 的核心工程底座。
- `AGENTS.md`、`MEMORY.md`、`knowledge/KNOWLEDGE.md` 分别承担什么角色。
- `WorkspaceManager` 的作用。
- `HarnessAgent#workspaceFor(userId, sessionId)` 为什么返回的是无共享可变绑定的 Workspace 视图。
- Workspace 文件与 AgentState 的根本差异。
- 如何通过 WorkspaceManager 读取和写入工作区文件。
- 为什么后续 Long-Term Memory、Skills、SubAgent、Plan Mode 都会再次依赖 Workspace。

## 一、先把 AgentState 和 Workspace 分开

上一课刚学完：

```text
RuntimeContext
      |
      v
AgentState
      |
      v
AgentStateStore
```

这一课增加另一条线：

```text
HarnessAgent
      |
      +----------------------+
      |                      |
      v                      v
AgentState                Workspace
运行状态                   长期文件工作区
      |                      |
context                  AGENTS.md
permission               MEMORY.md
tool/task state          knowledge/
...                      skills/
                         agents/.../sessions/
```

最重要的结论：

> Workspace 不是 AgentStateStore 的另一种实现。

它们解决不同问题。

## 二、Workspace 是什么

可以把 Workspace 理解成 Agent 的“工作目录”。

典型布局逐渐会长成：

```text
workspace/
├── AGENTS.md
├── MEMORY.md
├── memory/
├── knowledge/
│   ├── KNOWLEDGE.md
│   └── ...
├── skills/
├── subagents/
└── agents/
    └── <agent>/
        └── sessions/
```

本节只使用三个最基础文件：

```text
AGENTS.md
MEMORY.md
knowledge/KNOWLEDGE.md
```

以及一个自己写入的：

```text
notes/learning-note.md
```

## 三、三个基础文件怎么理解

### AGENTS.md

它描述 Agent 在当前工作区中的行为约束和本地规则。

本案例写了：

```text
- 技术解释使用中文
- 先给结论，再解释原因
- 不要把 AgentState 和 Workspace 当成一个概念
```

这类信息更像“当前项目的工作说明”。

### MEMORY.md

本节只把它当作一个可读取的 Workspace 记忆文件来观察。

真正的长期记忆写入、检索、刷新机制会放到后面的 LongTermMemory 课程，不在这里提前展开。

### knowledge/KNOWLEDGE.md

它代表工作区提供给 Agent 的知识内容入口之一。

本节放入了上一课的核心结论：

```text
RuntimeContext != AgentState != Workspace
```

## 四、一步步把案例编码出来

### 第 1 步：准备 Workspace

模块中直接提交：

```text
12-HarnessWorkspace/.agentscope/workspace/
├── AGENTS.md
├── MEMORY.md
└── knowledge/
    └── KNOWLEDGE.md
```

这样启动案例时工作区内容是确定的，不依赖你本机已有文件。

### 第 2 步：交给 HarnessAgent

```java
return HarnessAgent.builder()
        .name("workspace-agent")
        .sysPrompt("...")
        .model(model)
        .workspace(Paths.get(".agentscope/workspace"))
        .build();
```

因为 Spring Boot Maven Plugin 的 working directory 是当前模块，所以路径最终指向：

```text
12-HarnessWorkspace/.agentscope/workspace
```

### 第 3 步：为请求创建 RuntimeContext

```java
RuntimeContext context = RuntimeContext.builder()
        .userId(userId)
        .sessionId(sessionId)
        .build();
```

Workspace 本身有静态文件，也可能存在按用户/会话隔离的运行数据，因此请求身份仍然重要。

### 第 4 步：取得当前请求对应的 WorkspaceManager

```java
WorkspaceManager workspace = agent.workspaceFor(userId, sessionId);
```

不要在并发 Controller 里通过修改一个共享 WorkspaceManager 的“当前用户”来切换会话。

`workspaceFor(...)` 返回针对该请求身份的视图，更符合 Harness 的并发设计。

### 第 5 步：直接读取核心文件

```java
workspace.readAgentsMd(context);
workspace.readMemoryMd(context);
workspace.readKnowledgeMd(context);
```

这一步完全不需要调用 LLM。

所以本节新增：

```text
GET /api/workspace/inspect?userId=alice&sessionId=s1
```

专门用来观察 Workspace 文件层。

### 第 6 步：列出 Knowledge 文件

```java
workspace.listKnowledgeFiles(context)
```

它返回当前知识目录中的文件集合。

### 第 7 步：写入一个学习笔记

```java
workspace.appendUtf8WorkspaceRelative(
        context,
        "notes/learning-note.md",
        content
);
```

然后再：

```java
workspace.readManagedWorkspaceFileUtf8(
        context,
        "notes/learning-note.md"
);
```

这样你会看到 Workspace 不只是 Prompt 加载入口，也是 Harness 的文件工作层。

## 五、启动

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"

./mvnw -pl 12-HarnessWorkspace spring-boot:run
```

## 六、实验一：不调用模型，直接检查 Workspace

```bash
curl 'http://localhost:18081/api/workspace/inspect?userId=alice&sessionId=s1'
```

你会直接得到：

```text
workspaceRoot
agentsMd
memoryMd
knowledgeMd
knowledgeFiles
learningNote
```

这一步非常重要。

它证明：

```text
Workspace 文件
      !=
模型生成结果
```

它们在模型运行前就已经真实存在。

## 七、实验二：写入 Workspace 文件

```bash
curl -X POST http://localhost:18081/api/workspace/note \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"s1",
    "content":"今天学到：Workspace 和 AgentState 的职责不同。"
  }'
```

响应会返回完整的 `notes/learning-note.md` 当前内容。

再次 inspect：

```bash
curl 'http://localhost:18081/api/workspace/inspect?userId=alice&sessionId=s1'
```

即可看到写入结果。

## 八、实验三：让 HarnessAgent 使用 Workspace

```bash
curl -X POST http://localhost:18081/api/workspace/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"s1",
    "message":"请解释 RuntimeContext、AgentState、Workspace 的区别，并遵守当前工作区规则。"
  }'
```

这个接口才真正进入 HarnessAgent 的推理流程。

你可以对照 `AGENTS.md` 观察回复风格是否遵守：

```text
先给结论
中文技术解释
不混淆 Workspace 与 AgentState
```

## 九、为什么 WorkspaceManager 值得单独认识

很多时候你不应该为了读一个 Workspace 文件就要求 LLM 自己调用工具。

应用代码可以直接：

```java
WorkspaceManager workspace = agent.workspaceFor(userId, sessionId);
String agentsMd = workspace.readAgentsMd(context);
```

这让 Workspace 同时服务于：

```text
Harness 内部能力
应用层管理接口
调试 / 运维接口
测试
```

## 十、Workspace 与普通 File IO 的区别

当然，你可以：

```java
Files.readString(...)
```

但 Harness 的 `WorkspaceManager` 不是简单的 `java.nio.file.Files` 包装。

它还承担：

- Workspace 路径语义。
- RuntimeContext 相关的 namespace。
- 本地文件与 Filesystem backend 的统一读取。
- session / memory / knowledge 等 Harness 约定目录。
- 后续远程/沙箱文件系统抽象。

因此业务代码进入 Harness 世界后，优先理解 WorkspaceManager 的抽象，而不是到处硬编码本地路径。

## 十一、自动化测试

测试使用 `@TempDir` 创建临时 Workspace：

```text
AGENTS.md
MEMORY.md
knowledge/KNOWLEDGE.md
```

然后：

```java
new WorkspaceManager(tempDir)
```

直接验证三个读取 API 和 Knowledge 文件列表。

不需要 DashScope，不调用模型。

运行：

```bash
./mvnw -pl 12-HarnessWorkspace test
```

## 十二、这节为什么放在 LongTermMemory 前面

后续你会看到越来越多能力依赖 Workspace：

```text
Long-Term Memory
    -> MEMORY.md / memory/

Skills
    -> skills/

SubAgent
    -> subagents/ / agents/

Plan Mode
    -> plans / task data

Session logs
    -> agents/.../sessions/
```

如果 Workspace 本身没搞懂，后面会把“AgentState、Memory、文件、Skill、Session Log”全部混在一起。

所以这一课是在给后面的 Harness 深水区打地基。

## 十三、常见误区

### 误区 1：Workspace 就是对话历史目录

不是。会话日志只是 Workspace 中可能出现的一部分内容。

### 误区 2：MEMORY.md 就等于 AgentState context

不是。

一个是 Workspace 文件体系的一部分，一个是 Agent 运行状态中的上下文。

### 误区 3：配置了 workspace 就等于学会长期记忆

不是。

Workspace 只是基础设施。长期记忆还有什么时候提取、什么时候写入、如何检索、如何注入等机制。

### 误区 4：Workspace 只能用本机 Files

Harness 抽象里还有 Filesystem backend、namespace、sandbox/remote 等能力。本节只使用最容易观察的本地模式。

## 十四、本节边界

本节只学习：

```text
Harness Workspace
WorkspaceManager
AGENTS.md
MEMORY.md
KNOWLEDGE.md
Workspace 文件读写
```

下一阶段再进入：

```text
13-LongTermMemory
14-ContextCompaction
15-RAG
```
