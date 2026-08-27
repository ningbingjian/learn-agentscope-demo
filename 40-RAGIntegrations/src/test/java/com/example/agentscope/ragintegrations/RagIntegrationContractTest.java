package com.example.agentscope.ragintegrations;

import com.example.agentscope.ragintegrations.service.RagIntegrationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagIntegrationContractTest {

    @Test
    void simpleKnowledgeRunsEndToEndWithoutExternalService() {
        RagIntegrationService service = new RagIntegrationService();
        var hits = service.retrieve("AgentScope 智能体是什么？");

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).get("id")).isEqualTo("agentscope");
    }

    @Test
    void catalogContainsAllFiveOfficialIntegrationFamilies() {
        RagIntegrationService service = new RagIntegrationService();
        assertThat(service.providers())
                .extracting(row -> row.get("name"))
                .containsExactly("simple", "dify", "ragflow", "haystack", "bailian");
    }
}
