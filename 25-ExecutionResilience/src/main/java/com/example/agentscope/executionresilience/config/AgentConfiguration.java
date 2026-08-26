package com.example.agentscope.executionresilience.config;

import com.example.agentscope.executionresilience.tool.ResilienceTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    ExecutionConfig modelExecutionConfig() {
        return ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(20))
                .maxAttempts(3)
                .initialBackoff(Duration.ofMillis(500))
                .maxBackoff(Duration.ofSeconds(3))
                .backoffMultiplier(2.0)
                .retryOn(ExecutionConfig.RETRYABLE_ERRORS)
                .build();
    }

    @Bean
    ExecutionConfig toolExecutionConfig() {
        return ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(3))
                .maxAttempts(1)
                .build();
    }

    @Bean(destroyMethod = "close")
    ReActAgent resilienceAgent(
            Model model,
            ResilienceTools resilienceTools,
            ExecutionConfig modelExecutionConfig,
            ExecutionConfig toolExecutionConfig
    ) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(resilienceTools);

        return ReActAgent.builder()
                .name("execution-resilience-agent")
                .sysPrompt("你是一个用于学习执行稳定性的助手。需要等待时必须调用 slow_task 工具。")
                .model(model)
                .toolkit(toolkit)
                .modelExecutionConfig(modelExecutionConfig)
                .toolExecutionConfig(toolExecutionConfig)
                .build();
    }
}
