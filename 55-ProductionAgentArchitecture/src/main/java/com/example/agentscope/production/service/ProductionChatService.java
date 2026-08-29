package com.example.agentscope.production.service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.extensions.mysql.store.JdbcStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import io.agentscope.harness.agent.skill.curator.SkillSecurityScanner;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ProductionChatService {
    private final ReActAgent agent;
    private final JdbcStore requestStore;
    private final ApplicationKnowledgeService knowledgeService;

    public ProductionChatService(
            ReActAgent agent, JdbcStore productionRequestStore,
            ApplicationKnowledgeService knowledgeService) {
        this.agent = agent;
        this.requestStore = productionRequestStore;
        this.knowledgeService = knowledgeService;
    }

    public ChatResult chat(ChatRequest request) {
        validate(request);
        List<String> namespace = List.of("chat-request", request.userId(), request.sessionId());
        StoreItem existing = requestStore.get(namespace, request.requestId());
        if (existing != null) return fromStored(existing, true);

        boolean claimed = requestStore.putIfVersion(
                namespace,
                request.requestId(),
                Map.of("status", "PROCESSING", "message", request.message()),
                0L);
        if (!claimed) {
            StoreItem raced = requestStore.get(namespace, request.requestId());
            return raced != null ? fromStored(raced, true) : processingDuplicate(request);
        }

        try {
            return executeClaimed(request, namespace);
        } catch (RuntimeException e) {
            requestStore.put(
                    namespace,
                    request.requestId(),
                    Map.of(
                            "status", "FAILED",
                            "errorType", e.getClass().getSimpleName()));
            throw e;
        }
    }

    private ChatResult executeClaimed(ChatRequest request, List<String> namespace) {
        ApplicationKnowledgeService.RetrievedContext retrieved = knowledgeService.retrieve(request.message());
        SkillSecurityScanner.ScanResult scan =
                SkillSecurityScanner.scanSingleFile(retrieved.source(), retrieved.text());

        String modelInput = request.message()
                + "\n\n<retrieved_data trust=\"untrusted\" source=\"" + retrieved.source() + "\">\n"
                + retrieved.text()
                + "\n</retrieved_data>";

        RuntimeContext ctx = RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .put("requestId", request.requestId())
                .build();
        Msg reply = agent.call(new UserMessage(modelInput), ctx).block();
        String response = reply == null ? "" : reply.getTextContent();
        ChatUsage usage = reply == null ? null : reply.getUsage();
        int inputTokens = usage == null ? 0 : usage.getInputTokens();
        int outputTokens = usage == null ? 0 : usage.getOutputTokens();

        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("status", "DONE");
        stored.put("response", response);
        stored.put("retrievalSource", retrieved.source());
        stored.put("retrievalTrust", retrieved.trust());
        stored.put("retrievalVerdict", scan.verdict().name());
        stored.put("inputTokens", inputTokens);
        stored.put("outputTokens", outputTokens);
        requestStore.put(namespace, request.requestId(), stored);

        return new ChatResult(
                request.requestId(), "DONE", false, response,
                retrieved.source(), retrieved.trust(), scan.verdict().name(),
                inputTokens, outputTokens);
    }

    private static ChatResult fromStored(StoreItem item, boolean duplicate) {
        Map<String, Object> v = item.value();
        String status = String.valueOf(v.getOrDefault("status", "PROCESSING"));
        String fallback = "FAILED".equals(status)
                ? "request previously failed"
                : "request is already processing";
        return new ChatResult(
                item.key(), status, duplicate,
                String.valueOf(v.getOrDefault("response", fallback)),
                String.valueOf(v.getOrDefault("retrievalSource", "pending")),
                String.valueOf(v.getOrDefault("retrievalTrust", "UNTRUSTED_DATA")),
                String.valueOf(v.getOrDefault("retrievalVerdict", "PENDING")),
                number(v.get("inputTokens")), number(v.get("outputTokens")));
    }

    private static ChatResult processingDuplicate(ChatRequest request) {
        return new ChatResult(
                request.requestId(), "PROCESSING", true, "request is already processing",
                "pending", "UNTRUSTED_DATA", "PENDING", 0, 0);
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static void validate(ChatRequest request) {
        Objects.requireNonNull(request, "request");
        if (blank(request.userId()) || blank(request.sessionId()) || blank(request.requestId()) || blank(request.message())) {
            throw new IllegalArgumentException("userId, sessionId, requestId and message are required");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ChatRequest(String userId, String sessionId, String requestId, String message) {}

    public record ChatResult(
            String requestId,
            String status,
            boolean duplicate,
            String response,
            String retrievalSource,
            String retrievalTrust,
            String retrievalVerdict,
            int inputTokens,
            int outputTokens) {}
}
