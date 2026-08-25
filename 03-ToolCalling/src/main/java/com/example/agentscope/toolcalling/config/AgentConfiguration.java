package com.example.agentscope.toolcalling.config;

import com.example.agentscope.toolcalling.tool.CalculatorTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    CalculatorTools calculatorTools() {
        return new CalculatorTools();
    }

    @Bean(destroyMethod = "close")
    ReActAgent toolCallingAgent(Model model, CalculatorTools calculatorTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(calculatorTools);

        return ReActAgent.builder()
                .name("tool-calling-agent")
                .sysPrompt("你是一个严谨的计算助手。遇到算术问题时必须调用 calculate 工具，"
                        + "不要依靠心算；得到工具结果后，再用中文回答用户。")
                .model(model)
                .toolkit(toolkit)
                .build();
    }
}
