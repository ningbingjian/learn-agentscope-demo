package com.example.agentscope.harnessmemory.web;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final HarnessAgent agent;
    private final MemoryConfig memoryConfig;

    public MemoryController(HarnessAgent agent, MemoryConfig memoryConfig) {
        this.agent = agent;
        this.memoryConfig = memoryConfig;
    }

    @PostMapping("/chat")
    ChatResponse chat(@RequestBody ChatRequest request) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        requireText(request.message(), "message");

        RuntimeContext context = context(request.userId(), request.sessionId());
        Msg reply = agent.call(new UserMessage(request.message()), context).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }
        return new ChatResponse(request.userId(), request.sessionId(), reply.getTextContent());
    }

    @GetMapping("/snapshot")
    MemorySnapshot snapshot(
            @RequestParam String userId,
            @RequestParam String sessionId
    ) {
        requireText(userId, "userId");
        requireText(sessionId, "sessionId");
        RuntimeContext context = context(userId, sessionId);
        WorkspaceManager workspace = agent.workspaceFor(userId, sessionId);
        return new MemorySnapshot(
                userId,
                sessionId,
                workspace.readMemoryMd(context),
                readDailyFiles(workspace, context)
        );
    }

    @GetMapping("/config")
    MemoryConfigResponse config() {
        return new MemoryConfigResponse(
                memoryConfig.flushTrigger().mode().name(),
                memoryConfig.flushTrigger().minGap().toString(),
                memoryConfig.consolidationMinGap().toString(),
                memoryConfig.consolidationMaxTokens(),
                memoryConfig.dailyFileRetentionDays(),
                memoryConfig.sessionRetentionDays()
        );
    }

    private static List<DailyMemoryFile> readDailyFiles(
            WorkspaceManager workspace,
            RuntimeContext context
    ) {
        Path memoryDir = workspace.getMemoryDir(context);
        if (!Files.isDirectory(memoryDir)) {
            return List.of();
        }
        try (var paths = Files.list(memoryDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(MemoryController::readDailyFile)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list daily memory files", exception);
        }
    }

    private static DailyMemoryFile readDailyFile(Path path) {
        try {
            return new DailyMemoryFile(
                    path.getFileName().toString(),
                    Files.readString(path, StandardCharsets.UTF_8)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read memory file: " + path, exception);
        }
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
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

    public record DailyMemoryFile(String fileName, String content) {
    }

    public record MemorySnapshot(
            String userId,
            String sessionId,
            String curatedMemory,
            List<DailyMemoryFile> dailyMemoryFiles
    ) {
    }

    public record MemoryConfigResponse(
            String flushMode,
            String flushMinGap,
            String consolidationMinGap,
            int consolidationMaxTokens,
            int dailyFileRetentionDays,
            int sessionRetentionDays
    ) {
    }
}
