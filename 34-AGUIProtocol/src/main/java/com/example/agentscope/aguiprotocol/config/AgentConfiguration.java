package com.example.agentscope.aguiprotocol.config;

import com.example.agentscope.aguiprotocol.model.AguiDemoModel;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.core.model.Model;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {

    @Bean
    Model aguiDemoModel() {
        return new AguiDemoModel();
    }

    @Bean
    ReActAgent aguiAgent(Model aguiDemoModel) {
        return ReActAgent.builder()
                .name("agui-assistant")
                .description("Agent exposed through the AG-UI protocol")
                .sysPrompt("You are the deterministic AG-UI lesson assistant.")
                .model(aguiDemoModel)
                .build();
    }

    /**
     * The AG-UI Spring Boot starter deliberately does not invent an agent registry.
     * Registering this bean activates its MVC auto-configuration.
     */
    @Bean
    AguiAgentRegistry aguiAgentRegistry(ReActAgent aguiAgent) {
        AguiAgentRegistry registry = new AguiAgentRegistry();
        registry.register("assistant", aguiAgent);
        registry.register("default", aguiAgent);
        return registry;
    }
}
