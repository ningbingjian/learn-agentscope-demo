package com.example.agentscope.streamingevents.web;

import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEventSseMapperTest {

    @Test
    void mapsTextDeltaToSse() {
        TextBlockDeltaEvent event = new TextBlockDeltaEvent("reply-1", "block-1", "你好");

        ServerSentEvent<Map<String, Object>> sse = AgentEventSseMapper.toSse(event);

        assertThat(sse.event()).isEqualTo("text_block_delta");
        assertThat(sse.data())
                .containsEntry("type", "TEXT_BLOCK_DELTA")
                .containsEntry("replyId", "reply-1")
                .containsEntry("blockId", "block-1")
                .containsEntry("delta", "你好");
    }

    @Test
    void mapsToolCallToSse() {
        ToolCallStartEvent event = new ToolCallStartEvent(
                "reply-1",
                "tool-call-1",
                "calculate"
        );

        ServerSentEvent<Map<String, Object>> sse = AgentEventSseMapper.toSse(event);

        assertThat(sse.event()).isEqualTo("tool_call_start");
        assertThat(sse.data())
                .containsEntry("type", "TOOL_CALL_START")
                .containsEntry("toolCallId", "tool-call-1")
                .containsEntry("toolName", "calculate");
    }
}
