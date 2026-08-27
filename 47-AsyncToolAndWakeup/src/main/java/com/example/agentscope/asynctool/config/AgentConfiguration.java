package com.example.agentscope.asynctool.config;

import com.example.agentscope.asynctool.bus.LessonAsyncToolRegistry;
import com.example.agentscope.asynctool.bus.LessonMessageBus;
import com.example.agentscope.asynctool.model.AsyncToolDemoModel;
import com.example.agentscope.asynctool.tool.SlowReportTools;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    LessonMessageBus lessonMessageBus() { return new LessonMessageBus(); }

    @Bean
    LessonAsyncToolRegistry lessonAsyncToolRegistry() { return new LessonAsyncToolRegistry(); }

    @Bean
    Model asyncToolDemoModel() { return new AsyncToolDemoModel(); }

    @Bean
    Toolkit asyncToolkit(SlowReportTools tools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);
        return toolkit;
    }

    @Bean(destroyMethod = "close")
    HarnessAgent asyncAgent(Model asyncToolDemoModel, Toolkit asyncToolkit,
                            LessonMessageBus bus, LessonAsyncToolRegistry registry) {
        return HarnessAgent.builder()
                .name("async-tool-agent")
                .model(asyncToolDemoModel)
                .toolkit(asyncToolkit)
                .messageBus(bus)
                .asyncToolTimeout(Duration.ofMillis(60))
                .asyncToolRegistry(registry)
                .disableMemoryHooks()
                .disableMemoryTools()
                .disableCompaction()
                .disableSubagents()
                .build();
    }
}
