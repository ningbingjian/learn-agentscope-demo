package com.example.agentscope.agentinterrupt.config;

import com.example.agentscope.agentinterrupt.tool.DelayTools;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    HarnessAgent interruptAgent(Model model, DelayTools delayTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(delayTools);

        return HarnessAgent.builder()
                .name("interrupt-agent")
                .sysPrompt("你是一个用于学习 Agent 中断机制的测试助手。"
                        + "用户要求等待时必须调用 pause 工具，工具结束后再简短回复。")
                .model(model)
                .toolkit(toolkit)
                .workspace(Paths.get(".agentscope/workspace"))
                .build();
    }
}
