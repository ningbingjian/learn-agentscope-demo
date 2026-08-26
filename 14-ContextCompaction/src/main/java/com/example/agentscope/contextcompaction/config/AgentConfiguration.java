package com.example.agentscope.contextcompaction.config;

import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    CompactionConfig compactionConfig() {
        return CompactionConfig.builder()
                .triggerMessages(6)
                .triggerTokens(Integer.MAX_VALUE)
                .keepMessages(2)
                .keepTokens(0)
                .flushBeforeCompact(false)
                .offloadBeforeCompact(false)
                .prune(null)
                .build();
    }

    @Bean(destroyMethod = "close")
    HarnessAgent compactionAgent(Model model, CompactionConfig compactionConfig) {
        return HarnessAgent.builder()
                .name("context-compaction-agent")
                .sysPrompt("你是一个上下文压缩实验助手。请简洁回答每一轮问题，方便观察多轮消息如何被压缩。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .compaction(compactionConfig)
                .disableMemoryHooks()
                .disableMemoryTools()
                .build();
    }
}
