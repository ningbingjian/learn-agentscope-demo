package com.example.agentscope.contextandstatestore.web;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@RestController
@RequestMapping("/api/state")
public class StateController {

    private final ReActAgent inMemoryAgent;
    private final ReActAgent fileAgent;
    private final JsonFileAgentStateStore fileStateStore;

    public StateController(
            @Qualifier("inMemoryAgent") ReActAgent inMemoryAgent,
            @Qualifier("fileAgent") ReActAgent fileAgent,
            @Qualifier("jsonFileStateStore") JsonFileAgentStateStore fileStateStore
    ) {
        this.inMemoryAgent = inMemoryAgent;
        this.fileAgent = fileAgent;
        this.fileStateStore = fileStateStore;
    }

    @PostMapping("/{storeType}/chat")
    StateChatResponse chat(
            @PathVariable String storeType,
            @RequestBody StateChatRequest request
    ) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        requireText(request.message(), "message");

        ReActAgent agent = agentFor(storeType);
        RuntimeContext context = context(request.userId(), request.sessionId());
        Msg reply = agent.call(new UserMessage(request.message()), context).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }

        AgentState state = agent.getAgentState(context);
        return new StateChatResponse(
                normalizeStoreType(storeType),
                request.userId(),
                request.sessionId(),
                reply.getTextContent(),
                state.getContext().size(),
                state.getSummary()
        );
    }

    @GetMapping("/file/{userId}/{sessionId}")
    PersistedStateResponse persistedState(
            @PathVariable String userId,
            @PathVariable String sessionId
    ) {
        AgentState state = fileStateStore
                .get(userId, sessionId, "agent_state", AgentState.class)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No persisted agent_state for this user/session"
                ));

        return new PersistedStateResponse(
                userId,
                sessionId,
                state.getContext().size(),
                state.getSummary(),
                fileStateStore.getRootDirectory().toAbsolutePath().toString()
        );
    }

    @DeleteMapping("/file/{userId}/{sessionId}")
    DeleteStateResponse deletePersistedState(
            @PathVariable String userId,
            @PathVariable String sessionId
    ) {
        boolean existed = fileStateStore.exists(userId, sessionId);
        fileStateStore.delete(userId, sessionId);
        return new DeleteStateResponse(userId, sessionId, existed);
    }

    private ReActAgent agentFor(String storeType) {
        return switch (normalizeStoreType(storeType)) {
            case "memory" -> inMemoryAgent;
            case "file" -> fileAgent;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "storeType must be memory or file"
            );
        };
    }

    private static String normalizeStoreType(String storeType) {
        return storeType == null ? "" : storeType.toLowerCase(Locale.ROOT);
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must not be blank"
            );
        }
    }

    public record StateChatRequest(String userId, String sessionId, String message) {
    }

    public record StateChatResponse(
            String storeType,
            String userId,
            String sessionId,
            String reply,
            int contextMessageCount,
            String summary
    ) {
    }

    public record PersistedStateResponse(
            String userId,
            String sessionId,
            int contextMessageCount,
            String summary,
            String storeRoot
    ) {
    }

    public record DeleteStateResponse(String userId, String sessionId, boolean existedBeforeDelete) {
    }
}
