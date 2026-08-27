package com.example.agentscope.chatcompat.config;

import com.example.agentscope.chatcompat.model.ChatCompatDemoModel;
import io.agentscope.core.ReActAgent;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class AgentConfiguration {

    @Bean
    ChatCompatDemoModel chatCompatDemoModel() {
        return new ChatCompatDemoModel();
    }

    @Bean
    AtomicInteger chatCompletionsAgentCreationCounter() {
        return new AtomicInteger();
    }

    /**
     * Chat Completions is stateless in AgentScope 2.0.1. The web starter asks an
     * ObjectProvider for a fresh ReActAgent on each HTTP request, so this bean is prototype scoped.
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    ReActAgent chatCompletionsAgent(
            ChatCompatDemoModel model, AtomicInteger chatCompletionsAgentCreationCounter) {
        int instance = chatCompletionsAgentCreationCounter.incrementAndGet();
        return ReActAgent.builder()
                .name("chat-compat-agent-" + instance)
                .description("Fresh stateless Chat Completions request agent")
                .sysPrompt("You are the deterministic Chat Completions compatibility lesson agent.")
                .model(model)
                .build();
    }
}
