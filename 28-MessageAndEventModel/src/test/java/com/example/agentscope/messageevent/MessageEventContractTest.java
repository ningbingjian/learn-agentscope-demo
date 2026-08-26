package com.example.agentscope.messageevent;

import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageEventContractTest {

    @Test
    void messageIsAnOrderedListOfTypedContentBlocks() {
        ToolUseBlock toolUse = ToolUseBlock.builder()
                .id("call-1")
                .name("add_numbers")
                .input(Map.of("left", 20, "right", 22))
                .state(ToolCallState.PENDING)
                .build();
        ToolResultBlock result = ToolResultBlock.builder()
                .id("call-1")
                .name("add_numbers")
                .output(TextBlock.builder().text("42").build())
                .state(ToolResultState.SUCCESS)
                .build();

        Msg msg = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("before tool").build())
                .content(toolUse)
                .content(result)
                .content(TextBlock.builder().text("after tool").build())
                .build();

        assertThat(msg.getTextContent()).contains("before tool", "after tool");
        assertThat(msg.getContentBlocks(ToolUseBlock.class)).containsExactly(toolUse);
        assertThat(msg.getContentBlocks(ToolResultBlock.class)).containsExactly(result);
        assertThat(msg.getContent()).hasSize(4);
    }

    @Test
    void eventStreamUsesStartDeltaEndCorrelationKeys() {
        List<AgentEvent> events = List.of(
                new AgentStartEvent("session-1", "reply-1", "message-event-agent"),
                new TextBlockStartEvent("reply-1", "text-1"),
                new TextBlockDeltaEvent("reply-1", "text-1", "hello"),
                new TextBlockEndEvent("reply-1", "text-1"),
                new AgentEndEvent("reply-1")
        );

        assertThat(events)
                .extracting(event -> event.getType().name())
                .containsExactly(
                        "AGENT_START",
                        "TEXT_BLOCK_START",
                        "TEXT_BLOCK_DELTA",
                        "TEXT_BLOCK_END",
                        "AGENT_END"
                );
        TextBlockDeltaEvent delta = (TextBlockDeltaEvent) events.get(2);
        assertThat(delta.getReplyId()).isEqualTo("reply-1");
        assertThat(delta.getBlockId()).isEqualTo("text-1");
        assertThat(delta.getDelta()).isEqualTo("hello");
    }
}
