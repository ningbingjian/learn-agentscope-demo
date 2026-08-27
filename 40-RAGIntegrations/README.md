# 40-RAGIntegrations

本课目标：把第 15 课的“应用层 RAG”继续深入到 AgentScope Java 2.0.1 **仍然发布的官方 RAG Integration 生态**。

第 15 课解决的是架构边界：应用层负责 Retrieve，Agent 负责回答。本课解决的是工程选型：检索能力到底由谁提供，以及现有 AgentScope Adapter 如何接 Simple / Dify / RAGFlow / Haystack / Bailian。

> **2.0.1 版本边界必须先知道：**这些 RAG extension 在 2.0.1 中仍然存在并有官方 Integration 文档，但 Core `Knowledge`、`Document` 等 `io.agentscope.core.rag` 类型已经从 2.0.0 起标记为 `@Deprecated(forRemoval = true)`，源码明确建议“在 application layer 集成 retrieval”。因此本课用于理解/维护现有 Adapter、学习其检索实现；全新生产系统仍优先沿用第 15 课的 application-layer retrieval 边界，不应重新把业务强绑定到待移除的 Core RAG API。

---

## 1. 本课学习内容

完成后应该能回答：

1. `Knowledge` 在现存 AgentScope RAG Adapter 中是什么抽象？
2. Simple / Dify / RAGFlow / Haystack / Bailian 有什么区别？
3. 什么情况下自己维护 Embedding + Vector DB？
4. 什么情况下应该接第三方 RAG 平台？
5. `SimpleKnowledge` 的文档入库、Embedding、向量检索链路是什么？
6. 为什么 Embedding Model 与聊天 Model 是两个不同模型？
7. 向量库为什么可以从 InMemory 换成 PgVector / Milvus / Qdrant / Elasticsearch？
8. `RetrieveConfig` 如何控制 TopK、threshold？
9. 为什么新项目仍推荐 application-layer retrieval？

---

## 2. 官方 RAG Integration

AgentScope Java 2.0.1 仍发布五类 RAG 集成：

| Provider | Maven artifact | 核心类 | 适合场景 |
| --- | --- | --- | --- |
| Simple | `agentscope-extensions-rag-simple` | `SimpleKnowledge` | 自己控制 embedding + vector DB |
| Dify | `agentscope-extensions-rag-dify` | `DifyKnowledge` | 已使用 Dify Dataset |
| RAGFlow | `agentscope-extensions-rag-ragflow` | `RAGFlowKnowledge` | 已使用 RAGFlow |
| Haystack | `agentscope-extensions-rag-haystack` | `HayStackKnowledge` | Haystack 服务化检索 |
| Bailian | `agentscope-extensions-rag-bailian` | `BailianKnowledge` | 阿里云百炼知识库 |

这些 Adapter 背后实现方式不同，但现有统一契约是：

```text
query
  ↓
Knowledge.retrieve(...)
  ↓
List<Document>
```

再次强调：这条 `Knowledge/Document` 契约在 2.0.1 Core 中已经 `forRemoval`，所以理解它与继续在新架构里强依赖它是两回事。

---

## 3. SimpleKnowledge 最值得先掌握

第三方 RAG 平台会帮你封装大量细节。为了真正理解 RAG，本课默认使用 SimpleKnowledge。

完整链路：

```text
原始文档
   ↓
Reader
   ↓
Document
   ↓
Chunker
   ↓
EmbeddingModel
   ↓
vector
   ↓
VDBStoreBase
   ↓
query embedding
   ↓
similarity search
   ↓
Document + score
```

本课为了完全离线，把 Reader/Chunker 简化成手工创建 4 个 Document，但 `SimpleKnowledge + EmbeddingModel + InMemoryStore + RetrieveConfig` 都是真实 AgentScope 2.0.1 API。

---

## 4. 一步步编码

### Step 1：加入 RAG 扩展

`pom.xml` 同时引入五类官方 integration。

核心实验真正使用：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-simple</artifactId>
</dependency>
```

另外四个依赖让课程能够直接认识它们的真实类型，而不是只在文档里写名字。

### Step 2：实现本地 EmbeddingModel

`EmbeddingModel` 只有三个核心契约：

```java
Mono<double[]> embed(ContentBlock block);
String getModelName();
int getDimensions();
```

本课 `KeywordEmbeddingModel` 使用 4 维向量：

```text
[AgentScope, Redis, Sandbox, Skill]
```

例如：

```text
"AgentScope Java 智能体"
       ↓
[1, 0, 0, 0]
```

```text
"Redis 分布式存储"
       ↓
[0, 1, 0, 0]
```

目的不是模拟真实 Embedding 模型质量，而是让 RAG 的完整数据流在没有 API Key 的情况下可重复测试。

### Step 3：创建向量库

```java
InMemoryStore store = InMemoryStore.builder()
    .dimensions(embeddingModel.getDimensions())
    .build();
```

现有 extension 还提供：

```text
PgVectorStore
MilvusStore
QdrantStore
ElasticsearchStore
```

这里体现一个重要抽象：

```text
SimpleKnowledge
     │
     └── VDBStoreBase
              │
       ┌──────┼──────┐
       ↓      ↓      ↓
   PgVector Milvus Qdrant ...
```

### Step 4：组装 SimpleKnowledge

```java
SimpleKnowledge knowledge = SimpleKnowledge.builder()
    .embeddingModel(embeddingModel)
    .embeddingStore(store)
    .build();
```

### Step 5：创建 Document

```java
TextBlock block = TextBlock.builder()
    .text(text)
    .build();

Document doc = new Document(
    new DocumentMetadata(block, id, "0")
);
```

这里有一个容易误解的细节：

```text
Document#getId()
= 框架根据 doc_id + chunk_id + content 生成的确定性 UUID

Document#getMetadata().getDocId()
= 业务传入的 doc_id
```

因此本课 API 返回结果同时展示业务 `id` 和 `documentUuid`，不要把两者混为一谈。

### Step 6：写入文档

```java
knowledge.addDocuments(List.of(doc)).block();
```

内部会调用：

```text
Document ContentBlock
       ↓
EmbeddingModel.embed()
       ↓
double[]
       ↓
InMemoryStore.add
```

### Step 7：检索

```java
knowledge.retrieve(
    query,
    RetrieveConfig.builder()
        .limit(3)
        .scoreThreshold(0.0)
        .build()
).block();
```

返回结果中每个 Document 都带 score。

---

## 5. RetrieveConfig

常用参数：

```java
RetrieveConfig.builder()
    .limit(5)
    .scoreThreshold(0.5)
    .build();
```

可以把它理解为：

```text
limit
= 最多拿多少条

scoreThreshold
= 低于多少相似度直接不要
```

生产系统中 threshold 不能随便定死，应该结合 embedding 模型、数据分布和评估集调参。

---

## 6. Simple 与平台型 RAG 的区别

### Simple

你负责：

```text
Reader
Chunker
Embedding
Vector DB
Index lifecycle
Retrieval tuning
```

优点：控制力强。

缺点：工程工作多。

### Dify / RAGFlow / Haystack / Bailian

平台负责更多知识库生命周期：

```text
upload
parse
chunk
embed
index
retrieve
rerank
```

AgentScope 的现存 extension 做 Adapter：

```text
Application
   ↓
AgentScope RAG Adapter
   ↓
Platform API
```

---

## 7. Dify

核心类：

```text
DifyRAGClient
DifyKnowledge
RetrievalMode
RerankConfig
```

适合已经把企业文档上传到 Dify Dataset 的项目。

---

## 8. RAGFlow

核心类：

```text
RAGFlowKnowledge
RAGFlowDocumentConverter
```

典型场景：

```text
企业文档
  ↓
RAGFlow parsing/chunking/index
  ↓
Java application retrieval adapter
```

---

## 9. Haystack

核心类：

```text
HayStackKnowledge
```

适合 Python 侧已经用 Haystack 构建好检索 Pipeline，而 Java 服务只消费检索 API 的架构。

---

## 10. Bailian Knowledge

核心类：

```text
BailianKnowledge
```

适合阿里云百炼生态。知识库解析、索引、召回由云服务处理。

---

## 11. 和第 15 课的关系

第 15 课：

```text
Application Retriever
      ↓
Context Injection
      ↓
ReActAgent
```

第 40 课：

```text
现存 RAG Provider Adapter
      ↓
Simple / Dify / RAGFlow / Haystack / Bailian
      ↓
检索结果
```

两课不是互相替代，而是：

```text
第 40 课
= 看懂具体 provider/backend 怎么做 retrieval

第 15 课
= 决定新系统应该把 retrieval 放在哪一层
```

对于 AgentScope Java 2.0.1 新项目，仍建议：

```text
Application Layer
    ↓
Retriever Adapter
    ↓
Dify / RAGFlow / Simple vector DB / Bailian ...
    ↓
Context Injection
    ↓
Agent
```

这样可以复用 provider 能力，又不把业务绑定在 `forRemoval` 的 Core RAG API 上。

---

## 12. RAG 与 Memory 的区别

```text
RAG
= 查企业/公共知识

Memory
= 查这个用户过去发生过什么
```

例如：

```text
“公司的请假制度是什么？”
→ RAG

“我上次说喜欢住什么类型酒店？”
→ Memory
```

下一课专门学习 Memory Integration。

---

## 13. 启动

```bash
./mvnw -pl 40-RAGIntegrations spring-boot:run
```

查看支持的 Provider：

```bash
curl http://localhost:18081/api/rag/providers
```

真实执行本地向量检索：

```bash
curl 'http://localhost:18081/api/rag/retrieve?q=AgentScope'
```

```bash
curl 'http://localhost:18081/api/rag/retrieve?q=Redis'
```

```bash
curl 'http://localhost:18081/api/rag/retrieve?q=Sandbox'
```

---

## 14. 自动化测试

```bash
./mvnw -pl 40-RAGIntegrations test
```

测试真正覆盖：

```text
KeywordEmbeddingModel
     ↓
SimpleKnowledge.addDocuments
     ↓
InMemoryStore
     ↓
SimpleKnowledge.retrieve
     ↓
AgentScope document scoring
```

没有 mock Knowledge，也不访问任何外部 RAG 服务。

---

## 15. 建议实验

1. 把 dimensions 从 4 改成 8。
2. 自己增加 Kafka / MCP / Plan 等关键词维度。
3. 调整 scoreThreshold 看召回数量变化。
4. 把 InMemoryStore 换成 PgVector。
5. 使用 TextReader 导入一个 txt 文件。
6. 使用 PDFReader 导入 PDF。
7. 把 local embedding 换成 OllamaTextEmbedding。
8. 最后再连接 Dify 或 RAGFlow。
9. 自己写一个 application-layer `Retriever` 接口，把 SimpleKnowledge 或第三方 API 隔离在 Adapter 之后。

---

## 16. 本课结论

记住两条主线：

```text
Provider 不同
    ↓
检索实现不同
    ↓
最终都是给 Agent 提供相关上下文
```

以及：

```text
2.0.1 官方 RAG extensions 仍存在
        +
Core Knowledge / Document 已 forRemoval
        ↓
新系统保持 application-layer retrieval
```

真正重要的是保持检索层与 Agent 推理层解耦。
