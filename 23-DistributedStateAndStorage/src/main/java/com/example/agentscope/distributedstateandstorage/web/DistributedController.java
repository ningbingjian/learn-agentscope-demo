package com.example.agentscope.distributedstateandstorage.web;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.remote.store.InMemoryStore;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
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
@RequestMapping("/api/distributed")
public class DistributedController {

    private static final String SHARED_NOTE = "memory/shared-note.md";

    private final HarnessAgent agent;
    private final InMemoryStore baseStore;

    public DistributedController(HarnessAgent agent, InMemoryStore baseStore) {
        this.agent = agent;
        this.baseStore = baseStore;
    }

    @PostMapping("/chat")
    ChatResponse chat(@RequestBody ChatRequest request) {
        validate(request.userId(), request.sessionId());
        requireText(request.message(), "message");
        RuntimeContext context = context(request.userId(), request.sessionId());
        Msg reply = agent.call(new UserMessage(request.message()), context).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }
        return new ChatResponse(request.userId(), request.sessionId(), reply.getTextContent());
    }

    @PostMapping("/note")
    Map<String, Object> writeNote(@RequestBody NoteRequest request) {
        validate(request.userId(), request.sessionId());
        requireText(request.content(), "content");
        RuntimeContext context = context(request.userId(), request.sessionId());
        WorkspaceManager workspace = agent.workspaceFor(request.userId(), request.sessionId());
        workspace.appendUtf8WorkspaceRelative(context, SHARED_NOTE,
                request.content() + System.lineSeparator());
        return Map.of(
                "path", SHARED_NOTE,
                "content", workspace.readManagedWorkspaceFileUtf8(context, SHARED_NOTE),
                "baseStoreItems", baseStore.size()
        );
    }

    @GetMapping("/inspect")
    Inspection inspect(String userId, String sessionId) {
        validate(userId, sessionId);
        RuntimeContext context = context(userId, sessionId);
        WorkspaceManager workspace = agent.workspaceFor(userId, sessionId);
        AgentState state = agent.getDelegate().getAgentState(userId, sessionId);
        return new Inspection(
                userId,
                sessionId,
                state != null ? state.getContext().size() : 0,
                workspace.readManagedWorkspaceFileUtf8(context, SHARED_NOTE),
                baseStore.size()
        );
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
    }

    private static void validate(String userId, String sessionId) {
        requireText(userId, "userId");
        requireText(sessionId, "sessionId");
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
    }

    public record ChatRequest(String userId, String sessionId, String message) {}
    public record ChatResponse(String userId, String sessionId, String reply) {}
    public record NoteRequest(String userId, String sessionId, String content) {}
    public record Inspection(
            String userId,
            String sessionId,
            int contextMessages,
            String sharedNote,
            int baseStoreItems
    ) {}
}
