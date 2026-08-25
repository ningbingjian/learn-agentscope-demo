package com.example.agentscope.sessionmemory.config;

import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    HarnessAgent noteTakerAgent(Model model) {
        return HarnessAgent.builder()
                .name("note-taker")
                .sysPrompt("你是一个帮助用户做笔记的助手。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .build();
    }
}
