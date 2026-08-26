package com.example.agentscope.applicationrag.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    ReActAgent ragAnswerAgent(Model model) {
        return ReActAgent.builder()
                .name("application-rag-agent")
                .sysPrompt("你是一个严格基于检索上下文回答问题的助手。"
                        + "如果检索上下文不足，明确说知识库信息不足，不要编造事实。")
                .model(model)
                .build();
    }
}
