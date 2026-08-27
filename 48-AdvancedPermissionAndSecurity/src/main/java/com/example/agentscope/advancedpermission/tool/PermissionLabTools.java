package com.example.agentscope.advancedpermission.tool;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public final class PermissionLabTools {
    private PermissionLabTools() {}

    public static final class ReadOnlyTool extends ToolBase {
        public ReadOnlyTool() {
            super(ToolBase.builder().name("read_status").description("Read service status")
                    .inputSchema(objectSchema("target")).readOnly(true));
        }
        @Override public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.just(ToolResultBlock.text("status=UP"));
        }
    }

    public static final class MutationTool extends ToolBase {
        public MutationTool() {
            super(ToolBase.builder().name("mutate_config").description("Mutate application config")
                    .inputSchema(objectSchema("target")).readOnly(false));
        }
        @Override public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.just(ToolResultBlock.text("updated"));
        }
    }

    public static final class GuardedDeployTool extends ToolBase {
        public GuardedDeployTool() {
            super(ToolBase.builder().name("deploy_service").description("Deploy a service")
                    .inputSchema(objectSchema("target")).readOnly(false));
        }

        @Override
        public Mono<PermissionDecision> checkPermissions(Map<String, Object> input, PermissionContextState context) {
            String target = String.valueOf(input.getOrDefault("target", ""));
            if (target.startsWith("forbidden-")) {
                return Mono.just(PermissionDecision.deny("safety policy forbids target " + target));
            }
            if (target.startsWith("prod-")) {
                return Mono.just(safetyAsk("Production deployment requires explicit review: " + target));
            }
            return Mono.just(PermissionDecision.passthrough("normal deployment target"));
        }

        @Override
        public boolean matchRule(String ruleContent, Map<String, Object> input) {
            if (ruleContent == null) return true;
            String target = String.valueOf(input.getOrDefault("target", ""));
            return ruleContent.startsWith("prefix:") && target.startsWith(ruleContent.substring("prefix:".length()));
        }

        @Override public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.just(ToolResultBlock.text("deployed"));
        }
    }

    public static final class SensitivePathTool extends ToolBase {
        public SensitivePathTool() {
            super(ToolBase.builder().name("write_file").description("Write a file")
                    .inputSchema(objectSchema("path")).readOnly(false)
                    .dangerousFiles(List.of(".env", "secrets.txt"))
                    .dangerousDirectories(List.of(".ssh", ".kube")));
        }

        @Override
        public Mono<PermissionDecision> checkPermissions(Map<String, Object> input, PermissionContextState context) {
            String path = String.valueOf(input.getOrDefault("path", ""));
            if (isDangerousPath(path)) {
                return Mono.just(safetyAsk("Dangerous path requires explicit review: " + path));
            }
            return Mono.just(PermissionDecision.passthrough("normal path"));
        }

        @Override public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.just(ToolResultBlock.text("written"));
        }
    }

    private static PermissionDecision safetyAsk(String message) {
        return PermissionDecision.builder()
                .behavior(PermissionBehavior.ASK)
                .message(message)
                .decisionReason("safety policy")
                .build();
    }

    private static Map<String, Object> objectSchema(String property) {
        return Map.of("type", "object", "properties", Map.of(property, Map.of("type", "string")));
    }
}
