package com.example.agentscope.advancedtooling.web;

import com.example.agentscope.advancedtooling.domain.RequestProfile;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.state.AgentState;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/advanced-tooling")
public class AdvancedToolingController {

    private final ReActAgent agent;

    public AdvancedToolingController(ReActAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    ChatResponse chat(@RequestBody ChatRequest request) {
        validate(request);
        Msg reply = agent.call(
                new UserMessage(request.message()),
                context(request)
        ).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }
        return new ChatResponse(
                request.userId(),
                request.sessionId(),
                reply.getTextContent(),
                state(request.userId(), request.sessionId())
        );
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<AgentEvent> stream(@RequestBody ChatRequest request) {
        validate(request);
        return agent.streamEvents(
                new UserMessage(request.message()),
                context(request)
        );
    }

    @GetMapping("/state")
    ToolState state(String userId, String sessionId) {
        requireText(userId, "userId");
        requireText(sessionId, "sessionId");
        AgentState state = agent.getAgentState(userId, sessionId);
        List<String> activeGroups = state.getToolContext().getActivatedGroups();
        List<String> visibleSchemas = agent.getToolkit()
                .getToolSchemas(activeGroups)
                .stream()
                .map(ToolSchema::getName)
                .sorted()
                .toList();
        List<String> registeredTools = agent.getToolkit().getToolNames().stream().sorted().toList();
        return new ToolState(activeGroups, visibleSchemas, registeredTools);
    }

    private static RuntimeContext context(ChatRequest request) {
        RequestProfile profile = new RequestProfile(request.tenant(), request.locale());
        return RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .put(RequestProfile.class, profile)
                .build();
    }

    private static void validate(ChatRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request must not be null");
        }
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        requireText(request.message(), "message");
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
    }

    public record ChatRequest(
            String userId,
            String sessionId,
            String message,
            String tenant,
            String locale
    ) {
    }

    public record ChatResponse(
            String userId,
            String sessionId,
            String reply,
            ToolState toolState
    ) {
    }

    public record ToolState(
            List<String> activeGroups,
            List<String> visibleToolSchemas,
            List<String> registeredTools
    ) {
    }
}
