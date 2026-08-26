package com.example.agentscope.contextcompaction.web;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ConversationCompactor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/compaction")
public class CompactionController {

    private final HarnessAgent agent;
    private final CompactionConfig config;

    public CompactionController(HarnessAgent agent, CompactionConfig config) {
        this.agent = agent;
        this.config = config;
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

    @GetMapping("/state")
    StateResponse state(
            @RequestParam String userId,
            @RequestParam String sessionId
    ) {
        requireText(userId, "userId");
        requireText(sessionId, "sessionId");
        AgentState state = agent.getDelegate().getAgentState(userId, sessionId);
        List<MessageView> messages = state.getContext().stream()
                .map(msg -> new MessageView(
                        msg.getRole() == null ? null : msg.getRole().name(),
                        msg.getName(),
                        msg.getTextContent()
                ))
                .toList();
        long summaryCount = state.getContext().stream()
                .filter(msg -> ConversationCompactor.SUMMARY_MSG_NAME.equals(msg.getName()))
                .count();
        return new StateResponse(
                userId,
                sessionId,
                state.getContext().size(),
                summaryCount,
                messages
        );
    }

    @GetMapping("/config")
    ConfigResponse config() {
        return new ConfigResponse(
                config.getTriggerMessages(),
                config.getTriggerTokens(),
                config.getKeepMessages(),
                config.getKeepTokens(),
                config.isFlushBeforeCompact(),
                config.isOffloadBeforeCompact()
        );
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must not be blank");
        }
    }

    public record ChatRequest(String userId, String sessionId, String message) {
    }

    public record ChatResponse(String userId, String sessionId, String reply) {
    }

    public record MessageView(String role, String name, String text) {
    }

    public record StateResponse(
            String userId,
            String sessionId,
            int contextSize,
            long compactionSummaryCount,
            List<MessageView> messages
    ) {
    }

    public record ConfigResponse(
            int triggerMessages,
            int triggerTokens,
            int keepMessages,
            int keepTokens,
            boolean flushBeforeCompact,
            boolean offloadBeforeCompact
    ) {
    }
}
