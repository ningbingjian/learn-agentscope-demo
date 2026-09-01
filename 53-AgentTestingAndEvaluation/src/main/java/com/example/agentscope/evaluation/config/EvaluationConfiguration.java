package com.example.agentscope.evaluation.config;

import com.example.agentscope.evaluation.model.EvaluationModel;
import com.example.agentscope.evaluation.tool.RecordingWeatherTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EvaluationConfiguration {
    @Bean
    EvaluationModel evaluationModel() { return new EvaluationModel(); }

    @Bean(destroyMethod = "close")
    ReActAgent evaluationAgent(EvaluationModel model, RecordingWeatherTools weatherTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(weatherTools);
        return ReActAgent.builder()
                .name("evaluation-agent")
                .sysPrompt("Use get_weather for weather questions; answer greetings directly.")
                .model(model)
                .toolkit(toolkit)
                .build();
    }
}
