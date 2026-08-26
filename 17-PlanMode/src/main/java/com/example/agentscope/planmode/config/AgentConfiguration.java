package com.example.agentscope.planmode.config;

import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    HarnessAgent plannerAgent(Model model) {
        return HarnessAgent.builder()
                .name("planner-agent")
                .sysPrompt("你是一个谨慎的软件规划助手。面对重构、架构调整、跨文件修改等复杂任务时，"
                        + "优先进入 Plan Mode，先只读调查、写出计划，再请求退出计划阶段。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .enablePlanMode()
                .planFileDirectory("plans")
                .disableMemoryHooks()
                .disableCompaction()
                .build();
    }
}
