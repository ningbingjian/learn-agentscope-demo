package com.example.agentscope.externalhitl.config;

import com.example.agentscope.externalhitl.model.ExternalToolDemoModel;
import com.example.agentscope.externalhitl.tool.ExternalNotificationTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {

    @Bean
    ExternalNotificationTools externalNotificationTools() {
        return new ExternalNotificationTools();
    }

    @Bean
    ReActAgent externalToolAgent(ExternalNotificationTools tools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);
        return ReActAgent.builder()
                .name("external-tool-agent")
                .sysPrompt("你负责演示外部工具执行。当工具暂停后，等待外部结果，再根据结果完成回答。")
                .model(new ExternalToolDemoModel())
                .toolkit(toolkit)
                .stateStore(new InMemoryAgentStateStore())
                .build();
    }
}
