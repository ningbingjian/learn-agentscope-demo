package com.example.agentscope.harnessworkspace.web;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private static final String LEARNING_NOTE = "notes/learning-note.md";

    private final HarnessAgent agent;

    public WorkspaceController(HarnessAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    WorkspaceChatResponse chat(@RequestBody WorkspaceChatRequest request) {
        validateIdentity(request.userId(), request.sessionId());
        requireText(request.message(), "message");

        RuntimeContext context = context(request.userId(), request.sessionId());
        Msg reply = agent.call(new UserMessage(request.message()), context).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }

        return new WorkspaceChatResponse(
                request.userId(),
                request.sessionId(),
                reply.getTextContent()
        );
    }

    @PostMapping("/note")
    WorkspaceNoteResponse appendNote(@RequestBody WorkspaceNoteRequest request) {
        validateIdentity(request.userId(), request.sessionId());
        requireText(request.content(), "content");

        RuntimeContext context = context(request.userId(), request.sessionId());
        WorkspaceManager workspace = agent.workspaceFor(request.userId(), request.sessionId());
        workspace.appendUtf8WorkspaceRelative(
                context,
                LEARNING_NOTE,
                request.content() + System.lineSeparator()
        );

        return new WorkspaceNoteResponse(
                LEARNING_NOTE,
                workspace.readManagedWorkspaceFileUtf8(context, LEARNING_NOTE)
        );
    }

    @GetMapping("/inspect")
    WorkspaceInspection inspect(String userId, String sessionId) {
        validateIdentity(userId, sessionId);
        RuntimeContext context = context(userId, sessionId);
        WorkspaceManager workspace = agent.workspaceFor(userId, sessionId);

        List<String> knowledgeFiles = workspace.listKnowledgeFiles(context)
                .stream()
                .map(Path::toString)
                .toList();

        return new WorkspaceInspection(
                workspace.getWorkspace().toAbsolutePath().toString(),
                workspace.readAgentsMd(context),
                workspace.readMemoryMd(context),
                workspace.readKnowledgeMd(context),
                knowledgeFiles,
                workspace.readManagedWorkspaceFileUtf8(context, LEARNING_NOTE)
        );
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
    }

    private static void validateIdentity(String userId, String sessionId) {
        requireText(userId, "userId");
        requireText(sessionId, "sessionId");
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must not be blank"
            );
        }
    }

    public record WorkspaceChatRequest(String userId, String sessionId, String message) {
    }

    public record WorkspaceChatResponse(String userId, String sessionId, String reply) {
    }

    public record WorkspaceNoteRequest(String userId, String sessionId, String content) {
    }

    public record WorkspaceNoteResponse(String path, String currentContent) {
    }

    public record WorkspaceInspection(
            String workspaceRoot,
            String agentsMd,
            String memoryMd,
            String knowledgeMd,
            List<String> knowledgeFiles,
            String learningNote
    ) {
    }
}
