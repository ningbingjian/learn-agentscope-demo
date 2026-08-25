package com.example.agentscope.permissionhitl;

import com.example.agentscope.permissionhitl.support.HitlSupport;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HitlSupportTest {

    @Test
    void findsAskingToolAndBuildsApprovalMessage() {
        ToolUseBlock askingTool = ToolUseBlock.builder()
                .id("tool-call-1")
                .name("issue_refund")
                .input(Map.of("orderId", "O-1001", "amount", 299.0))
                .state(ToolCallState.ASKING)
                .build();

        Msg askingReply = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(List.of(askingTool))
                .build();

        List<ToolUseBlock> pending = HitlSupport.findLatestAskingTools(List.of(askingReply));
        Msg resumeMessage = HitlSupport.buildResumeMessage(true, pending);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getName()).isEqualTo("issue_refund");
        assertThat(resumeMessage.getMetadata()).containsKey(Msg.METADATA_CONFIRM_RESULTS);

        @SuppressWarnings("unchecked")
        List<ConfirmResult> results = (List<ConfirmResult>) resumeMessage.getMetadata()
                .get(Msg.METADATA_CONFIRM_RESULTS);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isConfirmed()).isTrue();
        assertThat(results.get(0).getToolCall().getId()).isEqualTo("tool-call-1");
    }
}
