package com.example.agentscope.mcpandtoolsconfig.config;

import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    boolean mcpDemoEnabled(@Value("${demo.mcp.enabled:false}") boolean enabled) {
        return enabled;
    }

    @Bean(destroyMethod = "close")
    HarnessAgent mcpAgent(Model model, boolean mcpDemoEnabled) {
        var builder = HarnessAgent.builder()
                .name("mcp-learning-agent")
                .sysPrompt("你是 MCP 学习助手。若可用工具能够直接完成任务，应优先使用工具，并说明结果来自工具调用。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .disableMemoryHooks()
                .disableCompaction();

        if (!mcpDemoEnabled) {
            builder.disableToolsConfig();
        }
        return builder.build();
    }
}
