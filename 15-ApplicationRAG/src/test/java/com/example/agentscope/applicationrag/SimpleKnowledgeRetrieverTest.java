package com.example.agentscope.applicationrag;

import com.example.agentscope.applicationrag.domain.RetrievedDocument;
import com.example.agentscope.applicationrag.service.SimpleKnowledgeRetriever;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleKnowledgeRetrieverTest {

    private final SimpleKnowledgeRetriever retriever = new SimpleKnowledgeRetriever();

    @Test
    void ranksRuntimeContextDocumentFirstForSessionQuestion() {
        List<RetrievedDocument> results = retriever.retrieve(
                "RuntimeContext 里的 userId 和 sessionId 如何定位会话 AgentState？",
                3
        );

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).document().id()).isEqualTo("runtime-context");
        assertThat(results.get(0).score()).isPositive();
    }

    @Test
    void returnsNoDocumentWhenNothingMatches() {
        assertThat(retriever.retrieve("量子引力弦理论实验数据", 3)).isEmpty();
    }

    @Test
    void respectsResultLimit() {
        int resultSize = retriever.retrieve("AgentScope context memory tool permission", 2).size();
        assertThat(resultSize).isLessThanOrEqualTo(2);
    }
}
