# 51-ContextBudgetAndCompactionDeepDive

## 本课目标

第 14 课已经学过最基本的 `old prefix -> summary + recent tail`。本课不重复“怎么压缩”，而是从 Context Engineering 角度解决三个问题：

1. 一个模型的 context window 应该怎么做预算；
2. 太多历史消息（depth）和单个超大 Tool Result（width）为什么要用不同机制；
3. AgentScope Java 2.0.1 默认的 dynamic compaction / pruning / eviction 到底如何计算和执行。

## 一、Context Window 不是免费无限桶

即使模型支持 128K，也不应该把 128K 全部留给 conversation history。

本课用一个明确预算示例：

| 区域 | Token |
|---|---:|
| System Prompt | 8K |
| Workspace + Skills | 8K |
| Tool Schemas | 12K |
| Memory | 8K |
| RAG | 20K |
| Conversation | 45K |
| Tool Results | 10K |
| Output Reserve | 17K |
| **合计** | **128K** |

这不是官方固定配额，而是一种工程方法：**先为输出、工具、检索和系统上下文留预算，再允许历史消息增长。**

## 二、四种容易混淆的“上下文大小”

```text
Model#getContextWindowSize()
= Provider 模型总 context window

HarnessAgent.maxContextTokens(...)
= WorkspaceContextMiddleware 渲染 workspace 上下文的预算

CompactionConfig.trigger/keep
= conversation history 的深度治理

ToolResultEvictionConfig.maxResultChars
= 单个 Tool Result 的宽度治理
```

尤其要注意：

```java
HarnessAgent.builder().maxContextTokens(8000)
```

**不是**把模型上下文限制为 8K；它只限制 workspace context 的渲染预算。

## 三、2.0.1 默认动态 Compaction

`CompactionConfig.builder().build()` 默认：

```text
triggerMessages = 50
triggerTokens   = 0      // dynamic
reserved        = 20,000
keepMessages    = 20
keepTokens      = -1     // dynamic
keepTokensMin   = 2,000
keepTokensMax   = 8,000
keepTokensRatio = 0.25
flushBeforeCompact   = true
offloadBeforeCompact = true
prune = enabled
```

当 Model 报告 context window 时：

```text
effectiveTrigger = contextWindow - reserved
```

128K 模型：

```text
128,000 - 20,000 = 108,000
```

动态保留尾部：

```text
usable = 108,000
rawKeep = usable * 0.25 = 27,000
keep = min(8,000, max(2,000, 27,000))
     = 8,000
```

所以本课测试明确验证：

```text
128K model -> trigger 108K -> keep 8K
```

如果 Model `getContextWindowSize()` 返回 0：

```text
trigger -> FALLBACK_TRIGGER_TOKENS = 160K
keepTokens -> 0
```

此时 preserved tail 回到 `keepMessages=20` 的消息数模式。

## 四、Compaction 真正发生在哪里

`CompactionMiddleware` 在每次 `onReasoning` 前检查：

```text
ReasoningInput
    ↓
threshold exceeded?
    ↓ yes
flush long-term memory
    ↓
offload raw conversation
    ↓
summary LLM call
    ↓
AgentState.context = [summary] + recent tail
    ↓
rebuild ReasoningInput
    ↓
next model call
```

所以 Compaction 改的不只是“给模型临时看一个 summary”，它会更新当前 AgentState 的工作上下文。

## 五、Width vs Depth

这是本课最重要的心智模型：

```text
一个 Tool 返回 500KB JSON
= context WIDTH 问题
= ToolResultEviction

会话已经累计 200 轮
= context DEPTH 问题
= Conversation Compaction
```

不能只靠 summary 解决一个单条 500KB Tool Result；也不应该为了一个大 Tool Result 把整个对话都 summary。

## 六、Tool Result Eviction 默认值

2.0.1 默认：

```text
maxResultChars = 80,000
previewChars   = 2,000
path           = large_tool_results
```

超限后：

```text
完整 Tool Result
      ↓
workspace filesystem
large_tool_results/<agent>/<callId>-<hash>

模型上下文
      ↓
compact placeholder
+ head preview
+ tail preview
+ read_file 路径提示
```

默认不会 eviction 的工具包括：

```text
read_file / write_file / edit_file
grep_files / glob_files / list_files
memory_search / memory_get / session_search
```

`execute`（shell output）反而没有被排除，因为它可能非常大。

## 七、本课做一个真实 Eviction 实验

测试不是只检查配置，而是直接实例化：

```java
LocalFilesystem filesystem = new LocalFilesystem(...);
ToolResultEvictionMiddleware middleware =
    new ToolResultEvictionMiddleware(filesystem, config);
```

构造 5,000 字符的 `ToolResultBlock`，阈值故意调成 1,000：

```text
5,000-char result
      ↓
ToolResultEvictionMiddleware.onReasoning
      ↓
full output written to filesystem
      ↓
AgentState 被替换为 placeholder
      ↓
ReasoningInput 也被替换为 placeholder
```

测试最后读取真实文件，确认文件仍是原始 5,000 字符。

## 八、Prune、Eviction、Compaction 的层次

`CompactionConfig` 里还默认启用了 `PruneConfig.defaults()`。可以把三层治理理解为：

```text
cheap / local
   │
   ├── argument truncation (optional)
   ├── aggregate old tool-result pruning
   ├── per-result eviction
   │
   ▼
expensive / LLM
   └── conversation summarization
```

优先使用便宜、确定性的局部削减，只有当会话深度仍然逼近 context window 时再做 summary。

## 九、Memory Flush 与 Offload 为什么在 summary 前

默认：

```text
flushBeforeCompact = true
offloadBeforeCompact = true
```

原因是 summary 会替换旧 prefix。如果旧内容中存在跨会话长期有价值的信息，应先提取到 Memory；如果需要审计/回放原始会话，应先 offload 原消息，再让工作上下文缩短。

## 十、输出也必须留预算

典型错误：

```text
contextWindow = 128K
input 已经塞到 127.8K
然后要求模型输出 8K
```

即使 Provider 接受请求，也会导致截断、错误或极差的生成空间。因此本课预算明确预留 `outputReserve`。

## 十一、Tool Schema 也是 Context

MCP / Tool 越多，不只是“功能越多”，还意味着每轮模型请求携带更多 Tool Schema。

所以第 30 课的 ToolGroup / `reset_equipped_tools` 与本课直接相关：

```text
100 tools permanently visible
= schema budget 持续膨胀

按任务激活 tool group
= 动态控制 schema budget
```

## 十二、RAG / Memory / Skills 也要预算

RAG topK、Memory facts、Skill instructions 都应该有上限：

```text
retrieve everything
!=
better context
```

生产系统应综合相关性、token 成本、recency、来源可信度和当前任务阶段做选择。

## 十三、接口实验

启动：

```bash
./mvnw -pl 51-ContextBudgetAndCompactionDeepDive spring-boot:run
```

看 128K 示例预算：

```bash
curl http://localhost:18081/api/context-budget/plan
```

看动态 Compaction：

```bash
curl 'http://localhost:18081/api/context-budget/compaction?contextWindow=128000'
```

看 eviction 默认值：

```bash
curl http://localhost:18081/api/context-budget/eviction
```

## 十四、测试

```bash
./mvnw -pl 51-ContextBudgetAndCompactionDeepDive test
```

覆盖：

- 128K budget 总和
- 2.0.1 dynamic trigger/keep 公式
- unknown context-window fallback
- default eviction config
- **真实 LocalFilesystem Tool Result eviction**
- AgentState 与 downstream ReasoningInput 均不再携带完整大输出
- 原始完整输出真实落盘

## 十五、与前后课程关系

```text
14 ContextCompaction
       ↓
30 Tool Surface
40 RAG / 41 Memory
       ↓
51 Context Engineering ← 本课
       ↓
52 State Consistency
53 Evaluation
54 Security Architecture
```

学完本课后，“上下文不够了就总结一下”会升级为一套有预算、有层次、有成本意识的 Context Engineering 策略。
