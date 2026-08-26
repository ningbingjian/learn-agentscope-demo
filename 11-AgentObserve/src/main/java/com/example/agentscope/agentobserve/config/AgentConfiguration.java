package com.example.agentscope.agentobserve.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    static final String RESEARCH_SESSION = "research-default";
    static final String WRITER_SESSION = "writer-default";

    @Bean(name = "researcherAgent", destroyMethod = "close")
    ReActAgent researcherAgent(Model model) {
        return ReActAgent.builder()
                .name("researcher-agent")
                .defaultSessionId(RESEARCH_SESSION)
                .sysPrompt("你是研究助手。针对用户主题给出事实清晰、条理简洁的研究笔记。")
                .model(model)
                .build();
    }

    @Bean(name = "writerAgent", destroyMethod = "close")
    ReActAgent writerAgent(Model model) {
        return ReActAgent.builder()
                .name("writer-agent")
                .defaultSessionId(WRITER_SESSION)
                .sysPrompt("你是写作助手。写作时优先利用上下文里其他 Agent 已经提供的研究结果，"
                        + "不要假装没有看到已观察到的消息。")
                .model(model)
                .build();
    }
}
