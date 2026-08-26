package com.example.agentscope.adminops.config;

import com.example.agentscope.adminops.model.AdminDemoModel;
import com.example.agentscope.adminops.tool.AdminDemoTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {

    @Bean
    AdminDemoTools adminDemoTools() {
        return new AdminDemoTools();
    }

    @Bean
    Model adminDemoModel() {
        return new AdminDemoModel();
    }

    @Bean
    Toolkit adminDemoToolkit(AdminDemoTools tools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);
        return toolkit;
    }

    @Bean
    ReActAgent adminDemoAgent(Model adminDemoModel, Toolkit adminDemoToolkit) {
        return ReActAgent.builder()
                .name("admin-demo-agent")
                .description("Agent exposed to the AgentScope Admin control plane")
                .sysPrompt("你是 Admin 控制面学习助手。")
                .model(adminDemoModel)
                .toolkit(adminDemoToolkit)
                .stateStore(new InMemoryAgentStateStore())
                .build();
    }
}
