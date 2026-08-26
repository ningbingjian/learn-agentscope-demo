package com.example.agentscope.contextandstatestore.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean("inMemoryStateStore")
    InMemoryAgentStateStore inMemoryStateStore() {
        return new InMemoryAgentStateStore();
    }

    @Bean("jsonFileStateStore")
    JsonFileAgentStateStore jsonFileStateStore() {
        return new JsonFileAgentStateStore(Paths.get(".agentscope/state"));
    }

    @Bean(name = "inMemoryAgent", destroyMethod = "close")
    ReActAgent inMemoryAgent(
            Model model,
            @Qualifier("inMemoryStateStore") InMemoryAgentStateStore stateStore
    ) {
        return newAgent("in-memory-agent", model, stateStore);
    }

    @Bean(name = "fileAgent", destroyMethod = "close")
    ReActAgent fileAgent(
            Model model,
            @Qualifier("jsonFileStateStore") JsonFileAgentStateStore stateStore
    ) {
        return newAgent("file-state-agent", model, stateStore);
    }

    private static ReActAgent newAgent(
            String name,
            Model model,
            io.agentscope.core.state.AgentStateStore stateStore
    ) {
        return ReActAgent.builder()
                .name(name)
                .sysPrompt("你是一个用于学习 AgentState 的助手。请记住用户明确告诉你的事实，回答简洁。")
                .model(model)
                .stateStore(stateStore)
                .build();
    }
}
