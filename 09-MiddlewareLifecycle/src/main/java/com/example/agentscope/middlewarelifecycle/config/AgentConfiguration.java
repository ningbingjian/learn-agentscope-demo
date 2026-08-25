package com.example.agentscope.middlewarelifecycle.config;

import com.example.agentscope.middlewarelifecycle.middleware.AgentExecutionLoggingMiddleware;
import com.example.agentscope.middlewarelifecycle.tool.MathTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    ReActAgent middlewareAgent(
            Model model,
            MathTools mathTools,
            AgentExecutionLoggingMiddleware loggingMiddleware
    ) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(mathTools);

        return ReActAgent.builder()
                .name("middleware-agent")
                .sysPrompt("你是一个用于观察 Agent 生命周期的助手。遇到两个整数相乘的问题时，"
                        + "必须调用 multiply 工具；得到工具结果后再用一句中文回答。")
                .model(model)
                .toolkit(toolkit)
                .middleware(loggingMiddleware)
                .build();
    }
}
