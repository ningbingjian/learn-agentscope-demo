package com.example.agentscope.executionresilience.web;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ExecutionConfig;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/resilience")
public class ResilienceController {

    private final ReActAgent agent;

    public ResilienceController(ReActAgent agent) {
        this.agent = agent;
    }

    @GetMapping("/config")
    Map<String, Object> config() {
        return Map.of(
                "model", describe(agent.getModelExecutionConfig()),
                "tool", describe(agent.getToolExecutionConfig())
        );
    }

    @PostMapping("/chat")
    ChatResponse chat(@RequestBody ChatRequest request) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        requireText(request.message(), "message");

        RuntimeContext context = RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .build();
        Msg reply = agent.call(new UserMessage(request.message()), context).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }
        return new ChatResponse(request.userId(), request.sessionId(), reply.getTextContent());
    }

    private static Map<String, Object> describe(ExecutionConfig config) {
        return Map.of(
                "timeoutMillis", config.getTimeout().toMillis(),
                "maxAttempts", config.getMaxAttempts(),
                "initialBackoffMillis", config.getInitialBackoff() == null ? 0L : config.getInitialBackoff().toMillis(),
                "maxBackoffMillis", config.getMaxBackoff() == null ? 0L : config.getMaxBackoff().toMillis()
        );
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must not be blank");
        }
    }

    public record ChatRequest(String userId, String sessionId, String message) {
    }

    public record ChatResponse(String userId, String sessionId, String reply) {
    }
}
