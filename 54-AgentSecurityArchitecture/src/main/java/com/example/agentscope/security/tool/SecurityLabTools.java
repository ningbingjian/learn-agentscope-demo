package com.example.agentscope.security.tool;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import java.util.Map;
import reactor.core.publisher.Mono;

public final class SecurityLabTools {
    private SecurityLabTools() {}

    public static final class ToolSurface {
        @Tool(name = "read_status", description = "Read application status", readOnly = true)
        public String readStatus() { return "UP"; }

        @Tool(name = "dangerous_shell", description = "Dangerous mutation example", readOnly = false)
        public String dangerousShell() { return "should not be exposed"; }
    }

    /** File-writing tool whose safety check cannot be bypassed by BYPASS mode. */
    public static final class SensitiveWriteTool extends ToolBase {
        public SensitiveWriteTool() {
            super(ToolBase.builder()
                    .name("write_file")
                    .description("Write a file after security checks")
                    .inputSchema(Map.of(
                            "type", "object",
                            "properties", Map.of("path", Map.of("type", "string"))))
                    .readOnly(false));
        }

        @Override
        public Mono<PermissionDecision> checkPermissions(
                Map<String, Object> input, PermissionContextState context) {
            String path = String.valueOf(input.getOrDefault("path", ""));
            if (isDangerousPath(path)) {
                return Mono.just(PermissionDecision.builder()
                        .behavior(io.agentscope.core.permission.PermissionBehavior.ASK)
                        .message("Sensitive path requires explicit confirmation: " + path)
                        .decisionReason("safety: dangerous path protection")
                        .build());
            }
            return Mono.just(PermissionDecision.passthrough("normal path"));
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.just(ToolResultBlock.text("demo only - no real file was written"));
        }
    }
}
