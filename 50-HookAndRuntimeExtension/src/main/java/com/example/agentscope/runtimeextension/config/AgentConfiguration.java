package com.example.agentscope.runtimeextension.config;

import com.example.agentscope.runtimeextension.middleware.CountingMiddleware;
import com.example.agentscope.runtimeextension.model.HookDemoModel;
import io.agentscope.core.ReActAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {
    @Bean HookDemoModel hookDemoModel() { return new HookDemoModel(); }
    @Bean CountingMiddleware countingMiddleware() { return new CountingMiddleware(); }

    @Bean(destroyMethod = "close")
    ReActAgent runtimeExtensionAgent(HookDemoModel model, CountingMiddleware middleware) {
        return ReActAgent.builder()
                .name("runtime-extension-agent")
                .sysPrompt("Base system prompt")
                .model(model)
                .middleware(middleware)
                .build();
    }
}
