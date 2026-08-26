package com.example.agentscope.a2aprotocol.config;

import com.example.agentscope.a2aprotocol.model.A2aDemoModel;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {

    @Bean
    Model a2aDemoModel() {
        return new A2aDemoModel();
    }

    /**
     * The A2A starter sees this ReActAgent bean and creates an AgentRunner,
     * AgentScopeA2aServer, AgentCardController and JSON-RPC controller around it.
     */
    @Bean
    ReActAgent a2aAgent(Model a2aDemoModel) {
        return ReActAgent.builder()
                .name("lesson-a2a-agent")
                .description("AgentScope Java 2.0.1 A2A lesson agent")
                .sysPrompt("You are the deterministic A2A lesson agent.")
                .model(a2aDemoModel)
                .build();
    }
}
