package com.example.agentscope.gatewayandchannel.web;

import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import org.springframework.http.codec.ServerSentEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class GatewayEventSseMapper {

    private GatewayEventSseMapper() {
    }

    static boolean supports(AgentEvent event) {
        return switch (event.getType()) {
            case AGENT_START,
                    TEXT_BLOCK_DELTA,
                    TOOL_CALL_START,
                    TOOL_RESULT_END,
                    SUBAGENT_EXPOSED,
                    AGENT_END -> true;
            default -> false;
        };
    }

    static ServerSentEvent<Map<String, Object>> toSse(AgentEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", event.getType().name());

        if (event.getSource() != null) {
            data.put("source", event.getSource());
        }
        if (event instanceof AgentStartEvent start) {
            data.put("replyId", start.getReplyId());
            data.put("sessionId", start.getSessionId());
            data.put("agentName", start.getName());
        } else if (event instanceof TextBlockDeltaEvent delta) {
            data.put("replyId", delta.getReplyId());
            data.put("delta", delta.getDelta());
        } else if (event instanceof ToolCallStartEvent start) {
            data.put("toolCallId", start.getToolCallId());
            data.put("toolName", start.getToolCallName());
        } else if (event instanceof ToolResultEndEvent end) {
            data.put("toolCallId", end.getToolCallId());
            data.put("toolName", end.getToolCallName());
            data.put("state", end.getState().name());
        } else if (event instanceof SubagentExposedEvent exposed) {
            data.put("subagentId", exposed.getSubagentId());
            data.put("agentId", exposed.getAgentId());
            data.put("sessionId", exposed.getSessionId());
            data.put("label", exposed.getLabel());
        } else if (event instanceof AgentEndEvent end) {
            data.put("replyId", end.getReplyId());
        }

        return ServerSentEvent.<Map<String, Object>>builder(data)
                .id(event.getId())
                .event(event.getType().name().toLowerCase(Locale.ROOT))
                .build();
    }
}
