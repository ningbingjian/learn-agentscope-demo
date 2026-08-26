package com.example.agentscope.kubernetes.web;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/production")
public class ProductionController {

    private final HarnessAgent agent;
    private final GracefulShutdownManager shutdownManager;
    private final Environment environment;

    public ProductionController(
            HarnessAgent agent,
            GracefulShutdownManager shutdownManager,
            Environment environment
    ) {
        this.agent = agent;
        this.shutdownManager = shutdownManager;
        this.environment = environment;
    }

    @GetMapping("/status")
    Map<String, Object> status() {
        boolean distributed = Arrays.asList(environment.getActiveProfiles()).contains("distributed");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profiles", environment.getActiveProfiles());
        result.put("distributedMode", distributed);
        result.put("shutdownState", shutdownManager.getState().name());
        result.put("acceptingRequests", shutdownManager.isAcceptingRequests());
        result.put("activeRequests", shutdownManager.getActiveRequestCount());
        return result;
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

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
    }

    public record ChatRequest(String userId, String sessionId, String message) {
    }

    public record ChatResponse(String userId, String sessionId, String reply) {
    }
}
