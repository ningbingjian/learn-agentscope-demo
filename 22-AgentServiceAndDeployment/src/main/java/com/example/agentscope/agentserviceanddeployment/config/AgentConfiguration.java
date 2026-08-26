package com.example.agentscope.agentserviceanddeployment.config;

import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    HarnessAgent protocolAgent(Model model) {
        return HarnessAgent.builder()
                .name("agent-service")
                .sysPrompt("你是一个通过 Agent Protocol 对外提供任务能力的 Agent 服务。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .checkRunning(false)
                .build();
    }

    @Bean
    WorkspaceManager protocolWorkspaceManager(HarnessAgent protocolAgent) {
        return protocolAgent.getWorkspaceManager();
    }
}
