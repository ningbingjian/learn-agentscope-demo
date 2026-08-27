package com.example.agentscope.ragintegrations.service;

import com.example.agentscope.ragintegrations.rag.KeywordEmbeddingModel;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.store.InMemoryStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagIntegrationService {

    private final SimpleKnowledge knowledge;
    private final Map<String, String> textById = new LinkedHashMap<>();

    public RagIntegrationService() {
        KeywordEmbeddingModel embeddingModel = new KeywordEmbeddingModel();
        InMemoryStore store = InMemoryStore.builder()
                .dimensions(embeddingModel.getDimensions())
                .build();
        this.knowledge = SimpleKnowledge.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();

        add("agentscope", "AgentScope Java 用 ReActAgent 和 HarnessAgent 构建可生产化智能体。 ");
        add("redis", "RedisDistributedStore 适合多副本 Agent 的低延迟状态、工作区和锁。 ");
        add("sandbox", "Sandbox 把 Agent 的 shell 和文件操作隔离到 Docker 或 Kubernetes。 ");
        add("skill", "Skill 通过 SKILL.md 保存可复用 SOP，Harness 会按需加载。 ");
    }

    public List<Map<String, Object>> retrieve(String query) {
        List<Document> hits = knowledge.retrieve(
                query,
                RetrieveConfig.builder().limit(3).scoreThreshold(0.0).build())
                .block();
        if (hits == null) {
            return List.of();
        }
        return hits.stream().map(doc -> Map.<String, Object>of(
                "id", doc.getId(),
                "text", textById.getOrDefault(doc.getId(), ""),
                "score", doc.getScore() == null ? 0.0 : doc.getScore()
        )).toList();
    }

    public List<Map<String, String>> providers() {
        return List.of(
                provider("simple", "agentscope-extensions-rag-simple", "io.agentscope.core.rag.knowledge.SimpleKnowledge", "自管 embedding + vector store"),
                provider("dify", "agentscope-extensions-rag-dify", "io.agentscope.core.rag.integration.dify.DifyKnowledge", "Dify Dataset API"),
                provider("ragflow", "agentscope-extensions-rag-ragflow", "io.agentscope.core.rag.integration.ragflow.RAGFlowKnowledge", "RAGFlow knowledge base"),
                provider("haystack", "agentscope-extensions-rag-haystack", "io.agentscope.core.rag.integration.haystack.HayStackKnowledge", "Haystack HTTP service"),
                provider("bailian", "agentscope-extensions-rag-bailian", "io.agentscope.core.rag.integration.bailian.BailianKnowledge", "阿里云百炼知识库")
        );
    }

    private Map<String, String> provider(String name, String artifact, String className, String useCase) {
        return Map.of("name", name, "artifact", artifact, "class", className, "useCase", useCase);
    }

    private void add(String id, String text) {
        textById.put(id, text);
        TextBlock block = TextBlock.builder().text(text).build();
        Document doc = new Document(new DocumentMetadata(block, id, "0"));
        knowledge.addDocuments(List.of(doc)).block();
    }
}
