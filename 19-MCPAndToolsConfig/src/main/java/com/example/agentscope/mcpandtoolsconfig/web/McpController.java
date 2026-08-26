package com.example.agentscope.mcpandtoolsconfig.web;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.tools.McpServerConfig;
import io.agentscope.harness.agent.tools.ToolsConfig;
import io.agentscope.harness.agent.tools.ToolsConfigLoader;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final HarnessAgent agent;
    private final boolean enabled;

    public McpController(HarnessAgent agent, boolean mcpDemoEnabled) {
        this.agent = agent;
        this.enabled = mcpDemoEnabled;
    }

    @GetMapping("/config")
    Map<String, Object> config() {
        ToolsConfig config = ToolsConfigLoader.load(agent.getWorkspaceManager())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tools.json not found"));

        Map<String, Object> servers = new LinkedHashMap<>();
        if (config.getMcpServers() != null) {
            config.getMcpServers().forEach((name, server) -> servers.put(name, toView(server)));
        }
        return Map.of(
                "mcpDemoEnabled", enabled,
                "deny", config.getDeny() == null ? List.of() : config.getDeny(),
                "allow", config.getAllow() == null ? List.of() : config.getAllow(),
                "mcpServers", servers
        );
    }

    @PostMapping("/chat")
    ChatResponse chat(@RequestBody ChatRequest request) {
        if (!enabled) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "MCP demo is disabled. Restart with MCP_DEMO_ENABLED=true"
            );
        }
        if (!StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
        String userId = StringUtils.hasText(request.userId()) ? request.userId() : "demo-user";
        String sessionId = StringUtils.hasText(request.sessionId()) ? request.sessionId() : "mcp-session";
        RuntimeContext context = RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
        Msg reply = agent.call(new UserMessage(request.message()), context).block();
        return new ChatResponse(userId, sessionId, reply == null ? "" : reply.getTextContent());
    }

    private static Map<String, Object> toView(McpServerConfig server) {
        return Map.of(
                "transport", server.getTransport(),
                "command", server.getCommand(),
                "args", server.getArgs() == null ? List.of() : server.getArgs(),
                "enableTools", server.getEnableTools() == null ? List.of() : server.getEnableTools(),
                "timeout", String.valueOf(server.getTimeout()),
                "initializationTimeout", String.valueOf(server.getInitializationTimeout())
        );
    }

    public record ChatRequest(String userId, String sessionId, String message) {
    }

    public record ChatResponse(String userId, String sessionId, String reply) {
    }
}
