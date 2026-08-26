package com.example.agentscope.advancedtooling.config;

import com.example.agentscope.advancedtooling.tool.ContextAwareTools;
import com.example.agentscope.advancedtooling.tool.DatabaseTools;
import com.example.agentscope.advancedtooling.tool.DeploymentTools;
import com.example.agentscope.advancedtooling.tool.ProgressTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.ToolGroup;
import io.agentscope.core.tool.ToolGroupScope;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    Toolkit advancedToolkit(
            ContextAwareTools contextAwareTools,
            ProgressTools progressTools,
            DatabaseTools databaseTools,
            DeploymentTools deploymentTools
    ) {
        Toolkit toolkit = new Toolkit();

        // Ungrouped tools stay visible all the time.
        toolkit.registerTool(contextAwareTools);
        toolkit.registerTool(progressTools);

        ToolGroup database = ToolGroup.builder()
                .name("database")
                .description("Read-only database lookup tools. Activate only for database questions.")
                .active(false)
                .scope(ToolGroupScope.META)
                .build();
        ToolGroup deployment = ToolGroup.builder()
                .name("deployment")
                .description("Read-only deployment planning tools. Activate only for deployment questions.")
                .active(false)
                .scope(ToolGroupScope.META)
                .build();

        toolkit.registerToolGroup(database);
        toolkit.registerToolGroup(deployment);
        toolkit.registration().tool(databaseTools).group("database").apply();
        toolkit.registration().tool(deploymentTools).group("deployment").apply();
        return toolkit;
    }

    @Bean(destroyMethod = "close")
    ReActAgent advancedToolingAgent(Model model, Toolkit advancedToolkit) {
        return ReActAgent.builder()
                .name("advanced-tooling-agent")
                .sysPrompt("你是一个用于学习高级 Tool 机制的助手。"
                        + "数据库问题先通过 reset_equipped_tools 激活 database；"
                        + "部署问题先激活 deployment；"
                        + "需要请求身份时调用 who_am_i；"
                        + "需要演示长任务进度时调用 progress_task。"
                        + "任务结束后尽量只保留当前真正需要的 Tool Group。")
                .model(model)
                .toolkit(advancedToolkit)
                .enableMetaTool(true)
                .build();
    }
}
