package com.example.agentscope.modelruntime.config;

import com.example.agentscope.modelruntime.model.RuntimeInspectionModel;
import com.example.agentscope.modelruntime.tool.RuntimeTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {
    @Bean
    RuntimeInspectionModel runtimeInspectionModel() {
        return new RuntimeInspectionModel();
    }

    @Bean
    GenerateOptions runtimeGenerateOptions() {
        return GenerateOptions.builder()
                .temperature(0.2)
                .maxTokens(512)
                .thinkingBudget(1024)
                .parallelToolCalls(false)
                .cacheControl(true)
                .seed(42L)
                .build();
    }

    @Bean
    Toolkit runtimeToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new RuntimeTools());
        return toolkit;
    }

    @Bean(destroyMethod = "close")
    ReActAgent modelRuntimeAgent(
            RuntimeInspectionModel model,
            GenerateOptions runtimeGenerateOptions,
            Toolkit runtimeToolkit) {
        return ReActAgent.builder()
                .name("model-runtime-agent")
                .sysPrompt("Inspect model runtime plumbing and answer concisely.")
                .model(model)
                .toolkit(runtimeToolkit)
                .generateOptions(runtimeGenerateOptions)
                .build();
    }
}
