package com.example.agentscope.messageevent.config;

import com.example.agentscope.messageevent.tool.MathTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    ReActAgent messageEventAgent(Model model, MathTools mathTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(mathTools);

        return ReActAgent.builder()
                .name("message-event-agent")
                .sysPrompt("你是一个用于学习 AgentScope Message 与 Event 模型的助手。"
                        + "如果用户要求两个整数相加，必须调用 add_numbers 工具；"
                        + "最终回答保持简洁。")
                .model(model)
                .toolkit(toolkit)
                .build();
    }
}
