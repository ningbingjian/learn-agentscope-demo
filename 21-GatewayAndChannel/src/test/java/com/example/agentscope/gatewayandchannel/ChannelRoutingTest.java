package com.example.agentscope.gatewayandchannel;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelRoutingTest {

    @TempDir
    Path tempDir;

    @Test
    void explicitSessionsStayIsolatedBehindChatUiChannel() {
        try (HarnessAgent agent = HarnessAgent.builder()
                .name("routing-test-agent")
                .model(new HistoryEchoModel())
                .workspace(tempDir)
                .disableMemoryHooks()
                .disableMemoryTools()
                .disableCompaction()
                .disableSubagents()
                .build()) {

            ChatUiChannel chat = agent.channel(ChatUiChannel.create());
            chat.send(SendOptions.of("alice", "session-a"), "topic-A").block();
            chat.send(SendOptions.of("alice", "session-b"), "topic-B").block();

            Msg sessionAReply = chat.send(
                    SendOptions.of("alice", "session-a"),
                    "recall-session-a"
            ).block();
            Msg sessionBReply = chat.send(
                    SendOptions.of("alice", "session-b"),
                    "recall-session-b"
            ).block();

            assertThat(sessionAReply).isNotNull();
            assertThat(sessionBReply).isNotNull();
            assertThat(sessionAReply.getTextContent())
                    .contains("topic-A")
                    .doesNotContain("topic-B");
            assertThat(sessionBReply.getTextContent())
                    .contains("topic-B")
                    .doesNotContain("topic-A");
        }
    }

    /**
     * Echoes all text currently visible to the model. This keeps the test at the
     * ChatUiChannel/Gateway boundary instead of reaching into the delegate's
     * internal state representation, which is not the ownership boundary of the
     * gateway session runtime.
     */
    private static final class HistoryEchoModel implements Model {

        @Override
        public Flux<ChatResponse> stream(
                List<Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            String history = messages.stream()
                    .map(Msg::getTextContent)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining(" | "));
            ContentBlock content = TextBlock.builder().text("history:" + history).build();
            return Flux.just(ChatResponse.builder()
                    .content(List.of(content))
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "history-echo-model";
        }
    }
}
