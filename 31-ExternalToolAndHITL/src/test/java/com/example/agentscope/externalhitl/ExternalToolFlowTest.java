package com.example.agentscope.externalhitl;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.externalhitl.model.ExternalToolDemoModel;
import com.example.agentscope.externalhitl.tool.ExternalNotificationTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalToolFlowTest {

    @Test
    void externalToolSuspendsWithoutExecutingBodyThenResumesWithExternalResult() {
        ExternalNotificationTools tools = new ExternalNotificationTools();
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);
        ReActAgent agent = ReActAgent.builder()
                .name("test-agent")
                .sysPrompt("test")
                .model(new ExternalToolDemoModel())
                .toolkit(toolkit)
                .stateStore(new InMemoryAgentStateStore())
                .build();
        RuntimeContext context = RuntimeContext.builder()
                .userId("alice")
                .sessionId("external-1")
                .build();

        Msg suspended = agent.call(new UserMessage("发送部署完成通知"), context).block();

        assertThat(suspended).isNotNull();
        assertThat(suspended.getGenerateReason()).isEqualTo(GenerateReason.TOOL_SUSPENDED);
        assertThat(tools.bodyExecutions()).isZero();
        ToolUseBlock pending = suspended.getFirstContentBlock(ToolUseBlock.class);
        assertThat(pending).isNotNull();
        assertThat(pending.getName()).isEqualTo("external_send_notification");

        ToolResultBlock result = ToolResultBlock.builder()
                .id(pending.getId())
                .name(pending.getName())
                .output(TextBlock.builder().text("operator accepted notification").build())
                .state(ToolResultState.SUCCESS)
                .build();
        Msg resultMessage = Msg.builder()
                .name("operator")
                .role(MsgRole.TOOL)
                .content(result)
                .build();

        Msg finalReply = agent.call(List.of(resultMessage), context).block();

        assertThat(finalReply).isNotNull();
        assertThat(finalReply.getTextContent()).contains("operator accepted notification");
        assertThat(tools.bodyExecutions()).isZero();
    }
}
