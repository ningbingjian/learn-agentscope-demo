package com.example.agentscope.subagentorchestration.config;

import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    HarnessAgent orchestratorAgent(Model model) {
        return HarnessAgent.builder()
                .name("orchestrator-agent")
                .sysPrompt("你是技术任务总协调者。遇到可以独立拆分的调研或评审任务时，优先使用工作区中声明的子 Agent，"
                        + "收集结果后再由你统一归纳。不要把所有重上下文任务都塞进主 Agent。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .disableMemoryHooks()
                .disableCompaction()
                .build();
    }
}
