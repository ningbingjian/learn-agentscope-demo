# 53-AgentTestingAndEvaluation

## 1. 为什么 Agent 不能只靠单元测试

传统后端测试经常验证：

```text
输入 A -> 精确输出 B
```

Agent 的行为却包含模型选择、Tool 选择、Tool 参数、Memory/RAG、HITL、多轮上下文、延迟和 Token 成本。因此“HTTP 200”或“没有抛异常”不能证明 Agent 是正确的。

本课建立一个最小但完整的 Agent Eval Harness：

```text
Dataset
  ↓
Scenario Runner
  ↓
真实 ReActAgent
  ↓
Tool / final answer / ChatUsage
  ↓
Scorers
  ↓
Eval Report
  ↓
Release Gate
```

## 2. 2.0.1 的边界

AgentScope Java 2.0.1 没有单独的 `agentscope-evaluation` / `Evaluator` 框架。本课因此把 Dataset、Runner、Scorer 放在 application layer；被测试对象仍然使用真实 AgentScope：

- `ReActAgent`
- `RuntimeContext`
- `ToolUseBlock / ToolResultBlock`
- `ChatUsage`
- `Toolkit`

这和第 15 课 Application RAG 的原则类似：框架没有稳定抽象时，不要为了“看起来官方”硬造不存在的 API。

## 3. Dataset

`src/main/resources/eval-cases.json`：

```json
{
  "id": "weather-beijing",
  "input": "What is the weather in Beijing?",
  "expectedTool": "get_weather",
  "expectedCity": "Beijing",
  "expectedTextContains": "Beijing:sunny",
  "maxTotalTokens": 60
}
```

固定 Dataset 的意义是：换模型、改 Prompt、改 Tool 描述以后，重新跑同一批场景才能判断是否 regression。

## 4. 行为指标

本课计算：

```text
Tool Selection Accuracy
Argument Accuracy
Final Answer Correctness
Total Tokens
Latency
Estimated Cost
```

真实生产还可以继续加：

- Structured Output schema accuracy
- RAG Recall / Precision
- Memory Recall
- Permission Decision Accuracy
- HITL path coverage
- SubAgent delegation accuracy
- hallucination / citation faithfulness
- P50/P95/P99 latency

## 5. 为什么使用 deterministic Model

本课不是为了证明某个在线模型今天的表现，而是学习**评测工程结构**。`EvaluationModel` 用确定规则制造：

```text
weather query
  ↓
get_weather(city=...)
  ↓
ToolResult
  ↓
final answer
```

这样 CI 不需要 API Key，也不会因为在线模型随机波动导致 flaky test。

把这套 Eval Harness 接到真实模型时，只需替换 Model Bean，Dataset/Scorer/Gate 可以保留。

## 6. ChatUsage 与成本

AgentScope 2.0.1 的 `ChatUsage` 原生包含：

```text
inputTokens
outputTokens
cachedTokens
time
```

本课的 deterministic Model 每次响应都返回真实 `ChatUsage`，同时累计本 case 的 token。Runner 用示例单价计算：

```text
input  $0.001 / 1K
output $0.002 / 1K
```

这只是教学价格，不代表任何真实模型价格。生产中应该为每个 Provider/Model 配置真实 price catalog。

## 7. Eval Gate

本课 Gate：

```text
passRate == 100%
toolSelectionAccuracy == 100%
argumentAccuracy == 100%
```

生产可以设置：

```text
critical dataset = 100%
normal dataset >= 97%
P95 latency <= baseline * 1.10
cost <= baseline * 1.05
```

一旦模型升级造成关键场景回归，CI/灰度发布应该阻止上线，而不是等用户反馈。

## 8. Test Pyramid

```text
                E2E
                 ▲
              Scenario
                 ▲
          Agent Behavior Eval
                 ▲
        Tool / Middleware Contract
                 ▲
              Unit Test
```

越往下越快、越确定；越往上越接近真实用户行为。不要只写 E2E，也不要只写纯 Java Unit Test。

## 9. 启动

```bash
./mvnw -pl 53-AgentTestingAndEvaluation spring-boot:run
```

```bash
curl http://localhost:18081/api/eval/dataset
curl http://localhost:18081/api/eval/run
curl http://localhost:18081/api/eval/philosophy
```

## 10. 自动化测试

```bash
./mvnw -pl 53-AgentTestingAndEvaluation test
```

测试会让 3 个 Dataset Case 真正经过 ReActAgent，其中两个必须调用 `get_weather`，一个 greeting 不应该调用 Tool，并验证最终 Gate、Token 与 Cost 都有结果。

## 11. 后续扩展

把 Dataset 文件纳入版本控制后，每次下面这些变更都跑 Eval：

```text
Model version
System Prompt
Tool description/schema
Skill
RAG retriever
Memory policy
Compaction policy
Permission policy
```

这就是从“Demo Agent”迈向“可回归验证的 Agent 产品”的关键一步。
