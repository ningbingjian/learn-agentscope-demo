package com.example.agentscope.observabilityandtracing.config;

import com.example.agentscope.observabilityandtracing.middleware.ObservabilityMetricsMiddleware;
import com.example.agentscope.observabilityandtracing.tool.DiagnosticTools;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    HarnessAgent observableAgent(
            Model model,
            DiagnosticTools diagnosticTools,
            ObservabilityMetricsMiddleware metricsMiddleware
    ) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(diagnosticTools);

        return HarnessAgent.builder()
                .name("observable-agent")
                .sysPrompt("你是一个可观测性学习 Agent。需要检查组件状态时优先调用 service_status 工具。")
                .model(model)
                .toolkit(toolkit)
                .workspace(Paths.get(".agentscope/workspace"))
                .enableAgentTracingLog(true)
                .middleware(metricsMiddleware)
                .middleware(new OtelTracingMiddleware())
                .build();
    }
}
