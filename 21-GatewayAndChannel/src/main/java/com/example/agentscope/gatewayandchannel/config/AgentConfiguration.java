package com.example.agentscope.gatewayandchannel.config;

import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    HarnessAgent gatewayAgent(Model model) {
        return HarnessAgent.builder()
                .name("gateway-main-agent")
                .sysPrompt("你是 Gateway/Channel 学习主 Agent。普通问题直接回答；"
                        + "需要独立技术调研时可委派 researcher 子 Agent。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .disableMemoryHooks()
                .disableCompaction()
                .build();
    }

    @Bean
    ChatUiChannel chatUiChannel(HarnessAgent gatewayAgent) {
        return gatewayAgent.channel(ChatUiChannel.create());
    }
}
