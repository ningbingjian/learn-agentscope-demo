package com.example.agentscope.multiuserconcurrency.config;

import com.example.agentscope.multiuserconcurrency.tool.DelayTools;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    DelayTools delayTools() {
        return new DelayTools();
    }

    @Bean(destroyMethod = "close")
    HarnessAgent concurrencyAgent(Model model, DelayTools delayTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(delayTools);

        return HarnessAgent.builder()
                .name("concurrency-agent")
                .sysPrompt("你是一个并发测试助手。用户要求等待时，必须调用 pause 工具，"
                        + "等待完成后再用一句中文回复，不要自行估算等待时间。")
                .model(model)
                .toolkit(toolkit)
                .workspace(Paths.get(".agentscope/workspace"))
                .build();
    }
}
