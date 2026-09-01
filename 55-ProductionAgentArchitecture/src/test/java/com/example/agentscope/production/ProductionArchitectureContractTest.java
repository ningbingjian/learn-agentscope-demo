package com.example.agentscope.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.production.middleware.ProductionTelemetryMiddleware;
import com.example.agentscope.production.service.ApplicationKnowledgeService;
import com.example.agentscope.production.service.ProductionArchitectureService;
import com.example.agentscope.production.service.ProductionChatService;
import com.example.agentscope.production.service.ProductionEvalService;
import com.example.agentscope.production.tool.ProductionTools;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductionArchitectureContractTest {
    @Autowired ProductionChatService chatService;
    @Autowired ProductionEvalService evalService;
    @Autowired ProductionArchitectureService architectureService;
    @Autowired ApplicationKnowledgeService knowledgeService;
    @Autowired ProductionTelemetryMiddleware telemetry;
    @Autowired ProductionTools tools;

    @BeforeEach
    void reset() {
        telemetry.reset();
        tools.reset();
    }

    @Test
    void completeRequestPathUsesRetrievalSessionToolAndUsage() {
        String id = UUID.randomUUID().toString();
        ProductionChatService.ChatResult result = chatService.chat(
                new ProductionChatService.ChatRequest("alice", "s-" + id, "r-" + id, "check order A1001"));

        assertThat(result.status()).isEqualTo("DONE");
        assertThat(result.duplicate()).isFalse();
        assertThat(result.response()).contains("A1001", "SHIPPED");
        assertThat(result.retrievalTrust()).isEqualTo("UNTRUSTED_DATA");
        assertThat(result.inputTokens()).isGreaterThan(0);
        assertThat(tools.calls()).isEqualTo(1);
        assertThat(telemetry.snapshot()).containsEntry("actingCalls", 1);
    }

    @Test
    void nextTurnDoesNotReusePreviousTurnToolResult() {
        String id = UUID.randomUUID().toString();
        String sessionId = "multi-" + id;
        ProductionChatService.ChatResult first = chatService.chat(
                new ProductionChatService.ChatRequest("alice", sessionId, "r1-" + id, "check order A1001"));
        ProductionChatService.ChatResult second = chatService.chat(
                new ProductionChatService.ChatRequest("alice", sessionId, "r2-" + id, "hello again"));

        assertThat(first.response()).contains("SHIPPED");
        assertThat(second.response()).contains("production demo response");
        assertThat(second.response()).doesNotContain("SHIPPED");
        assertThat(tools.calls()).isEqualTo(1);
    }

    @Test
    void sameRequestIdIsIdempotentAndDoesNotRunAgentTwice() {
        String id = UUID.randomUUID().toString();
        ProductionChatService.ChatRequest request =
                new ProductionChatService.ChatRequest("bob", "session-" + id, "request-" + id, "hello production");

        ProductionChatService.ChatResult first = chatService.chat(request);
        ProductionChatService.ChatResult second = chatService.chat(request);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.response()).isEqualTo(first.response());
        assertThat(telemetry.snapshot()).containsEntry("agentCalls", 1);
    }

    @Test
    void maliciousRetrievalIsLabeledAsUntrustedAndScanned() {
        assertThat(knowledgeService.retrieve("malicious document").trust()).isEqualTo("UNTRUSTED_DATA");
        String id = UUID.randomUUID().toString();
        ProductionChatService.ChatResult result = chatService.chat(
                new ProductionChatService.ChatRequest("sec", "s-" + id, "r-" + id, "summarize malicious document"));
        assertThat(result.retrievalTrust()).isEqualTo("UNTRUSTED_DATA");
        assertThat(result.retrievalVerdict()).isNotEqualTo("SAFE");
        assertThat(result.response()).doesNotContain("id_rsa");
    }

    @Test
    void deterministicReleaseGatePasses() {
        assertThat(evalService.runGate().passed()).isTrue();
    }

    @Test
    void productionMatrixDoesNotPretendLocalDemoIsProductionReady() {
        assertThat(architectureService.readiness())
                .containsEntry("localDemoReady", true)
                .containsEntry("productionReadyByDefault", false);
        assertThat(architectureService.decisions())
                .extracting(ProductionArchitectureService.Decision::layer)
                .contains("Model", "Agent state", "Idempotency", "Sandbox", "Evaluation", "Deployment");
    }
}
