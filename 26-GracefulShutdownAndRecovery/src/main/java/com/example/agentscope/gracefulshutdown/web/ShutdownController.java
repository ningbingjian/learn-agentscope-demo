package com.example.agentscope.gracefulshutdown.web;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/shutdown-demo")
public class ShutdownController {

    private final ReActAgent agent;
    private final GracefulShutdownManager manager;

    public ShutdownController(ReActAgent agent, GracefulShutdownManager manager) {
        this.agent = agent;
        this.manager = manager;
    }

    @GetMapping("/status")
    Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", manager.getState().name());
        result.put("acceptingRequests", manager.isAcceptingRequests());
        result.put("activeRequests", manager.getActiveRequestCount());
        result.put("shutdownTimeoutSeconds", manager.getConfig().shutdownTimeout().getSeconds());
        result.put("partialReasoningPolicy", manager.getConfig().partialReasoningPolicy().name());
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
        return new ChatResponse(
                request.userId(),
                request.sessionId(),
                reply.getTextContent(),
                reply.getGenerateReason().name()
        );
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must not be blank");
        }
    }

    public record ChatRequest(String userId, String sessionId, String message) {
    }

    public record ChatResponse(String userId, String sessionId, String reply, String generateReason) {
    }
}
