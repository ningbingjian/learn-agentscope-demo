# 15-ApplicationRAG

本节学习 AgentScope Java 2.0.1 中推荐的 **application-layer RAG**。

先说明版本事实：2.0.1 代码里仍保留 `GenericRAGHook / Knowledge / RAGMode` 等兼容类型，但 `GenericRAGHook` 已经标记为 `@Deprecated(forRemoval = true)`，javadoc 明确写着：

```text
The rag package is removed; integrate retrieval at the application layer.
```

所以本节不教一个准备被删除的 Hook，而是直接搭出生产中更常见的边界：

```text
业务应用负责 retrieval
        ↓
检索结果组装为 context
        ↓
ReActAgent 负责 reasoning / generation
```

## 学习目标

完成本节后，你应该能够理解：

- RAG 的核心不是某一个框架 Hook，而是一条 retrieval pipeline。
- 为什么 Retrieval 与 Generation 应该解耦。
- Query、Retriever、Retrieved Documents、Context Injection、LLM Answer 五步如何衔接。
- 为什么返回 sources 很重要。
- 为什么本例使用简单关键词检索，而生产通常替换成 embedding + vector database。
- AgentScope 2.0.1 中 RAG 应该放在哪一层。

## 1. RAG 到底是什么

RAG = Retrieval-Augmented Generation。

核心流程：

```text
User Question
      ↓
Retriever
      ↓
Relevant Documents
      ↓
Prompt / Context Injection
      ↓
LLM / Agent
      ↓
Grounded Answer
```

它解决的问题是：

> 模型本身不知道我的私有知识，或者知识可能已经变化，如何在回答前先给它相关资料？

## 2. 为什么 2.0.1 不继续用 GenericRAGHook

旧思路：

```text
ReActAgent
   ↓ Hook
GenericRAGHook
   ↓
Knowledge.retrieve()
```

但 2.0.1 已经把这些 Core RAG 类型标记为待删除，并要求 retrieval 集成到 application layer。

新边界更清楚：

```text
Controller / Service
    ├── Retriever
    ├── Vector DB / Search
    ├── ACL / tenant filtering
    ├── rerank
    └── source tracking
            ↓
        ReActAgent
```

真实企业 RAG 本来就有大量业务逻辑，不适合全部藏在一个 Agent Hook 里。

## 3. 本节为什么不用向量数据库

为了只学习 RAG 主链路，本节使用一个确定性的关键词 Retriever：

```java
SimpleKnowledgeRetriever
```

内置几条 AgentScope 学习文档：

```text
RuntimeContext
Tool Calling
Permission HITL
Harness Memory
Context Compaction
```

每条文档有：

```java
record KnowledgeDocument(
    String id,
    String title,
    String content,
    List<String> keywords
)
```

检索器只负责：

```text
query -> score -> sort -> topK
```

以后替换成：

```text
Embedding Model
   +
Vector Database
   +
Reranker
```

Controller 和 Agent 层都不需要大改。

## 4. 项目结构

```text
15-ApplicationRAG
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/applicationrag
    │   │   ├── ApplicationRagApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   ├── domain
    │   │   │   ├── KnowledgeDocument.java
    │   │   │   └── RetrievedDocument.java
    │   │   ├── service/SimpleKnowledgeRetriever.java
    │   │   └── web/RagController.java
    │   └── resources/application.yml
    └── test/java/com/example/agentscope/applicationrag
        └── SimpleKnowledgeRetrieverTest.java
```

## 5. 第一步：知识文档

```java
public record KnowledgeDocument(
        String id,
        String title,
        String content,
        List<String> keywords
) {
}
```

生产中 document 通常还会有：

```text
sourceUrl
chunkId
tenantId
permissions
updatedAt
embedding
metadata
```

本例先保持最小化。

## 6. 第二步：Retriever

核心方法：

```java
public List<RetrievedDocument> retrieve(String query, int limit)
```

注意 Retriever **不调用 LLM**。

它只负责：

```text
输入 query
   ↓
找文档
   ↓
算相关度
   ↓
返回 topK
```

这使 retrieval 可以独立测试。

## 7. 第三步：先单独测试 retrieval

接口：

```text
GET /api/rag/search?q=...
```

例如：

```bash
curl 'http://localhost:18081/api/rag/search?q=RuntimeContext%20sessionId'
```

你应该首先看到：

```text
RuntimeContext 与会话状态
```

这一步非常重要。

如果检索结果都错了，再好的 LLM 也救不了 RAG。

## 8. 第四步：Context Injection

Controller 将检索结果组装：

```text
<retrieved_knowledge>
[1] RuntimeContext 与会话状态
...
[2] ...
</retrieved_knowledge>

用户问题：...
```

然后再调用：

```java
agent.call(new UserMessage(prompt), context)
```

这就是 application-layer RAG 最核心的“增强”。

## 9. 为什么要限制模型只能使用检索结果

系统提示和请求 prompt 都明确要求：

```text
如果知识不足，回答“知识库信息不足”
不要补充检索结果之外的事实
```

这不能百分百消灭 hallucination，但建立了正确的 grounding 行为。

真正生产环境还需要：

```text
source citation
answer verification
confidence threshold
fallback
policy
```

## 10. 第五步：返回 Sources

本模块 API 不只返回 answer：

```json
{
  "question": "RuntimeContext 怎么定位会话？",
  "answer": "...",
  "sources": [
    {
      "id": "runtime-context",
      "title": "RuntimeContext 与会话状态",
      "score": 12
    }
  ]
}
```

为什么 sources 很重要？

因为 RAG 不是：

```text
“模型说了什么”
```

而是：

```text
“模型基于哪些资料说了什么”
```

## 11. 启动

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 15-ApplicationRAG spring-boot:run
```

## 12. 实验一：只检索，不调用模型

```bash
curl 'http://localhost:18081/api/rag/search?q=Permission%20ASK%20人工确认'
```

预期第一条接近：

```text
Permission 与 HITL
```

## 13. 实验二：完整 RAG

```bash
curl -X POST http://localhost:18081/api/rag/ask \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"rag-demo",
    "question":"AgentScope 中 RuntimeContext 的 userId 和 sessionId 有什么作用？"
  }'
```

完整链路：

```text
question
   ↓
SimpleKnowledgeRetriever
   ↓
top 3 documents
   ↓
buildPrompt()
   ↓
ReActAgent
   ↓
answer + sources
```

## 14. 实验三：知识库不知道的问题

```bash
curl -X POST http://localhost:18081/api/rag/ask \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"rag-demo-2",
    "question":"2026 年某公司第四季度财报是多少？"
  }'
```

Retriever 应该找不到相关文档。

Prompt 会包含：

```text
(no relevant documents retrieved)
```

Agent 应该明确表示知识库信息不足。

## 15. 自动化测试

Retriever 可以完全脱离模型测试：

```bash
./mvnw -pl 15-ApplicationRAG test
```

测试重点：

- RuntimeContext 查询时 runtime-context 文档排名第一；
- 无关问题返回空；
- topK 限制有效。

这也是应用层 retrieval 的一个重要优势：可测性非常好。

## 16. 从 Demo 到生产 RAG

本例：

```text
关键词匹配
```

生产可以逐步替换：

```text
Document ingestion
      ↓
Chunking
      ↓
Embedding
      ↓
Vector DB
      ↓
Metadata / ACL filter
      ↓
Hybrid Search
      ↓
Reranker
      ↓
TopK Context
      ↓
Agent
```

AgentScope 的 Agent 层不用承担所有检索基础设施职责。

## 17. Memory 和 RAG 不一样

```text
Memory
来源：用户和 Agent 历史交互
目的：记住用户与长期项目状态

RAG
来源：外部知识库、文档、数据库、搜索系统
目的：回答时检索相关事实
```

两者经常一起使用，但职责不同。

## 18. Compaction 和 RAG 也不一样

```text
Compaction
处理：当前 session 已有上下文
目标：控制 context window

RAG
处理：外部知识
目标：把相关知识动态加入上下文
```

## 19. 本节边界

本节不展开：

- embedding model；
- vector database；
- BM25；
- hybrid search；
- reranker；
- document ingestion pipeline；
- 多租户知识权限。

这些可以在后面的生产化 RAG 专题继续学习。
