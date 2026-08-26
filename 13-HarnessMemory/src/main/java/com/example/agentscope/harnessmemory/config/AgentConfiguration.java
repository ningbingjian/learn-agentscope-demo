package com.example.agentscope.harnessmemory.config;

import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    MemoryConfig memoryConfig() {
        return MemoryConfig.builder()
                .flushTrigger(MemoryConfig.FlushTrigger.always())
                .consolidationMinGap(Duration.ofMinutes(30))
                .dailyFileRetentionDays(90)
                .sessionRetentionDays(180)
                .build();
    }

    @Bean(destroyMethod = "close")
    HarnessAgent memoryAgent(Model model, MemoryConfig memoryConfig) {
        return HarnessAgent.builder()
                .name("harness-memory-agent")
                .sysPrompt("你是一个长期记忆学习助手。优先利用 Harness Workspace 中的 MEMORY.md "
                        + "回答用户，并在适合时让框架自动提炼长期事实。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .memory(memoryConfig)
                .disableCompaction()
                .build();
    }
}
