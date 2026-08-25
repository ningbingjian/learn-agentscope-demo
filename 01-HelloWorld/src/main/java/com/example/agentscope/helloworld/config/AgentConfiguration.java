package com.example.agentscope.helloworld.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    ReActAgent helloWorldAgent(Model model) {
        return ReActAgent.builder()
                .name("hello-world")
                .sysPrompt("你是一个简洁、友好的 AI 助手。")
                .model(model)
                .build();
    }
}
