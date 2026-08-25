package com.example.agentscope.harnessworkspace.config;

import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    HarnessAgent workspaceAgent(Model model) {
        return HarnessAgent.builder()
                .name("workspace-agent")
                .sysPrompt("你是一个学习型技术助手。请遵守 Workspace 中的 AGENTS.md，"
                        + "并在相关问题中优先使用 Workspace 已提供的知识。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .build();
    }
}
