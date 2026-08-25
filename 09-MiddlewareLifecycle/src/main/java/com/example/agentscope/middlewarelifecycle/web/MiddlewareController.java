package com.example.agentscope.middlewarelifecycle.web;

import com.example.agentscope.middlewarelifecycle.middleware.AgentExecutionLoggingMiddleware;
import com.example.agentscope.middlewarelifecycle.middleware.AgentExecutionLoggingMiddleware.LifecycleSnapshot;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/middleware")
public class MiddlewareController {

    private final ReActAgent agent;
    private final AgentExecutionLoggingMiddleware middleware;

    public MiddlewareController(
            ReActAgent agent,
            AgentExecutionLoggingMiddleware middleware
    ) {
        this.agent = agent;
        this.middleware = middleware;
    }

    @PostMapping("/chat")
    MiddlewareChatResponse chat(@RequestBody MiddlewareChatRequest request) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        requireText(request.message(), "message");

        RuntimeContext context = RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .build();

        Msg reply = agent.call(List.of(new UserMessage(request.message())), context).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }

        return new MiddlewareChatResponse(
                request.userId(),
                request.sessionId(),
                reply.getGenerateReason().name(),
                reply.getTextContent(),
                middleware.snapshot()
        );
    }

    @GetMapping("/metrics")
    LifecycleSnapshot metrics() {
        return middleware.snapshot();
    }

    @PostMapping("/metrics/reset")
    LifecycleSnapshot resetMetrics() {
        middleware.reset();
        return middleware.snapshot();
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must not be blank"
            );
        }
    }

    public record MiddlewareChatRequest(String userId, String sessionId, String message) {
    }

    public record MiddlewareChatResponse(
            String userId,
            String sessionId,
            String generateReason,
            String reply,
            LifecycleSnapshot lifecycle
    ) {
    }
}
