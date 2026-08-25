package com.example.agentscope.permissionhitl.support;

import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HitlSupport {

    private HitlSupport() {
    }

    public static List<ToolUseBlock> extractAskingTools(Msg message) {
        if (message == null) {
            return List.of();
        }
        return message.getContent().stream()
                .filter(ToolUseBlock.class::isInstance)
                .map(ToolUseBlock.class::cast)
                .filter(tool -> tool.getState() == ToolCallState.ASKING)
                .toList();
    }

    public static List<ToolUseBlock> findLatestAskingTools(List<Msg> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }

        for (int index = context.size() - 1; index >= 0; index--) {
            List<ToolUseBlock> askingTools = extractAskingTools(context.get(index));
            if (!askingTools.isEmpty()) {
                return askingTools;
            }
        }
        return List.of();
    }

    public static Msg buildResumeMessage(boolean approved, List<ToolUseBlock> askingTools) {
        if (askingTools == null || askingTools.isEmpty()) {
            throw new IllegalArgumentException("askingTools must not be empty");
        }

        List<ConfirmResult> confirmResults = new ArrayList<>();
        for (ToolUseBlock askingTool : askingTools) {
            confirmResults.add(new ConfirmResult(approved, askingTool));
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(Msg.METADATA_CONFIRM_RESULTS, confirmResults);

        return Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(approved ? "approved" : "denied")
                .metadata(metadata)
                .build();
    }
}
