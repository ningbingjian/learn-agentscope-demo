package com.example.agentscope.skills.config;

import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    HarnessAgent skillAgent(Model model) {
        return HarnessAgent.builder()
                .name("skill-agent")
                .sysPrompt("你是一个会主动复用标准工作方法的工程助手。先根据 available skills 判断是否有匹配能力，"
                        + "如果匹配，应先加载对应 SKILL.md 或参考文件，再按技能步骤执行，不要只凭记忆猜流程。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .disableMemoryHooks()
                .disableCompaction()
                .build();
    }
}
