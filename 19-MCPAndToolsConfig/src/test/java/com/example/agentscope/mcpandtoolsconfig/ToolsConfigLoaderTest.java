package com.example.agentscope.mcpandtoolsconfig;

import io.agentscope.harness.agent.tools.McpServerConfig;
import io.agentscope.harness.agent.tools.ToolsConfig;
import io.agentscope.harness.agent.tools.ToolsConfigLoader;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ToolsConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsMcpServerAndToolFilterWithoutStartingExternalProcess() throws Exception {
        Files.writeString(tempDir.resolve("tools.json"), """
                {
                  "deny": ["execute"],
                  "mcpServers": {
                    "demo": {
                      "transport": "stdio",
                      "command": "python3",
                      "args": ["server.py"],
                      "enableTools": ["echo_text"],
                      "timeout": "PT20S"
                    }
                  }
                }
                """);

        WorkspaceManager workspace = new WorkspaceManager(tempDir);
        ToolsConfig config = ToolsConfigLoader.load(workspace).orElseThrow();

        assertThat(config.getDeny()).containsExactly("execute");
        assertThat(config.getMcpServers()).containsKey("demo");
        McpServerConfig server = config.getMcpServers().get("demo");
        assertThat(server.getTransport()).isEqualTo("stdio");
        assertThat(server.getCommand()).isEqualTo("python3");
        assertThat(server.getEnableTools()).containsExactly("echo_text");
        assertThat(server.getTimeout().getSeconds()).isEqualTo(20);
    }
}
