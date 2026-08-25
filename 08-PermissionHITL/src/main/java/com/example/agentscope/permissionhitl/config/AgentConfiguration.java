package com.example.agentscope.permissionhitl.config;

import com.example.agentscope.permissionhitl.tool.RefundTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    PermissionContextState refundPermissionContext() {
        PermissionRule askBeforeRefund = new PermissionRule(
                "issue_refund",
                null,
                PermissionBehavior.ASK,
                "lesson-policy"
        );

        return PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT)
                .addAskRule("issue_refund", askBeforeRefund)
                .build();
    }

    @Bean(destroyMethod = "close")
    ReActAgent refundAgent(
            Model model,
            RefundTools refundTools,
            PermissionContextState refundPermissionContext
    ) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(refundTools);

        return ReActAgent.builder()
                .name("refund-agent")
                .sysPrompt("你是一个退款助手。用户要求退款时，必须调用 issue_refund 工具，"
                        + "不要绕过工具，也不要声称已经退款。工具需要权限确认时等待用户决定；"
                        + "确认后根据真实工具结果简短回复。")
                .model(model)
                .toolkit(toolkit)
                .permissionContext(refundPermissionContext)
                .build();
    }
}
