package com.example.agentscope.observabilityandtracing.web;

import com.example.agentscope.observabilityandtracing.middleware.ObservabilityMetricsMiddleware;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    private final HarnessAgent agent;
    private final ObservabilityMetricsMiddleware metrics;

    public ObservabilityController(HarnessAgent agent, ObservabilityMetricsMiddleware metrics) {
        this.agent = agent;
        this.metrics = metrics;
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
        return new ChatResponse(reply.getTextContent(), metrics.snapshot());
    }

    @GetMapping("/metrics")
    ObservabilityMetricsMiddleware.Snapshot metrics() {
        return metrics.snapshot();
    }

    @PostMapping("/metrics/reset")
    ObservabilityMetricsMiddleware.Snapshot reset() {
        metrics.reset();
        return metrics.snapshot();
    }

    @GetMapping("/trace-model")
    Map<String, Object> traceModel() {
        return Map.of(
                "harnessLogging", "AgentTraceMiddleware",
                "otelMiddleware", "OtelTracingMiddleware",
                "spans", List.of(
                        "invoke_agent <agent-name>",
                        "chat <model-name>",
                        "execute_tool <tool-name>"
                )
        );
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
    }

    public record ChatRequest(String userId, String sessionId, String message) {}
    public record ChatResponse(
            String reply,
            ObservabilityMetricsMiddleware.Snapshot metrics
    ) {}
}
