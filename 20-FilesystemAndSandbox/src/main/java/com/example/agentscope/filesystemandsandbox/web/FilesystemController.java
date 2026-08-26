package com.example.agentscope.filesystemandsandbox.web;

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

import java.util.Map;

@RestController
@RequestMapping("/api/fs")
public class FilesystemController {

    private final HarnessAgent agent;
    private final boolean sandboxEnabled;

    public FilesystemController(HarnessAgent agent, boolean sandboxDemoEnabled) {
        this.agent = agent;
        this.sandboxEnabled = sandboxDemoEnabled;
    }

    @GetMapping("/info")
    Map<String, Object> info() {
        return Map.of(
                "mode", sandboxEnabled ? "docker-sandbox" : "local-rooted",
                "sandboxEnabled", sandboxEnabled,
                "localIsolationScope", "USER",
                "sandboxIsolationScope", "SESSION",
                "workspace", agent.getWorkspaceManager().getWorkspace().toAbsolutePath().normalize().toString()
        );
    }

    @PostMapping("/chat")
    ChatResponse chat(@RequestBody ChatRequest request) {
        if (!StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
        String userId = StringUtils.hasText(request.userId()) ? request.userId() : "demo-user";
        String sessionId = StringUtils.hasText(request.sessionId()) ? request.sessionId() : "fs-session";
        RuntimeContext context = RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
        Msg reply = agent.call(new UserMessage(request.message()), context).block();
        return new ChatResponse(
                userId,
                sessionId,
                sandboxEnabled ? "docker-sandbox" : "local-rooted",
                reply == null ? "" : reply.getTextContent()
        );
    }

    public record ChatRequest(String userId, String sessionId, String message) {
    }

    public record ChatResponse(String userId, String sessionId, String mode, String reply) {
    }
}
