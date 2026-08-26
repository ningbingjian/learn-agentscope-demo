package com.example.agentscope.gracefulshutdown.config;

import com.example.agentscope.gracefulshutdown.tool.DelayTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.shutdown.GracefulShutdownConfig;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.shutdown.PartialReasoningPolicy;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    JsonFileAgentStateStore shutdownStateStore() {
        return new JsonFileAgentStateStore(Paths.get(".agentscope/state"));
    }

    @Bean
    GracefulShutdownManager gracefulShutdownManager() {
        GracefulShutdownManager manager = GracefulShutdownManager.getInstance();
        manager.setConfig(new GracefulShutdownConfig(
                Duration.ofSeconds(20),
                PartialReasoningPolicy.SAVE
        ));
        return manager;
    }

    @Bean(destroyMethod = "close")
    ReActAgent shutdownAgent(
            Model model,
            DelayTools delayTools,
            JsonFileAgentStateStore shutdownStateStore
    ) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(delayTools);

        return ReActAgent.builder()
                .name("graceful-shutdown-agent")
                .sysPrompt("你是一个用于学习优雅下线的助手。用户要求等待时必须调用 pause 工具。")
                .model(model)
                .toolkit(toolkit)
                .stateStore(shutdownStateStore)
                .build();
    }
}
