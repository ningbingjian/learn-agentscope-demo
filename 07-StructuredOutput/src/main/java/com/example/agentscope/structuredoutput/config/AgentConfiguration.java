package com.example.agentscope.structuredoutput.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    ReActAgent ticketAnalysisAgent(Model model) {
        return ReActAgent.builder()
                .name("ticket-analysis-agent")
                .sysPrompt("你是一个客服工单分析助手。请根据用户描述输出结构化工单分析。"
                        + "category 只能使用 PAYMENT、ACCOUNT、DELIVERY、PRODUCT、OTHER；"
                        + "priority 只能使用 LOW、MEDIUM、HIGH；"
                        + "summary 用一句中文概括；只有需要人工进一步处理时 needHuman 才为 true。")
                .model(model)
                .build();
    }
}
