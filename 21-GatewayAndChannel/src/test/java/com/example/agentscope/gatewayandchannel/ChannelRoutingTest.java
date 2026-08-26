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

import static org.assertj.core.api.Assertions.assertThat;

class ChannelRoutingTest {

    @TempDir
    Path tempDir;

    @Test
    void explicitSessionsStayIsolatedBehindChatUiChannel() {
        try (HarnessAgent agent = HarnessAgent.builder()
                .name("routing-test-agent")
                .model(new EchoModel())
                .workspace(tempDir)
                .disableMemoryHooks()
                .disableMemoryTools()
                .disableCompaction()
                .disableSubagents()
                .build()) {

            ChatUiChannel chat = agent.channel(ChatUiChannel.create());
            chat.send(SendOptions.of("alice", "session-a"), "topic-A").block();
            chat.send(SendOptions.of("alice", "session-b"), "topic-B").block();

            List<Msg> sessionA = agent.getDelegate().getAgentState("alice", "session-a").getContext();
            List<Msg> sessionB = agent.getDelegate().getAgentState("alice", "session-b").getContext();

            assertThat(sessionA).anyMatch(msg -> msg.getTextContent().contains("topic-A"));
            assertThat(sessionA).noneMatch(msg -> msg.getTextContent().contains("topic-B"));
            assertThat(sessionB).anyMatch(msg -> msg.getTextContent().contains("topic-B"));
            assertThat(sessionB).noneMatch(msg -> msg.getTextContent().contains("topic-A"));
        }
    }

    private static final class EchoModel implements Model {

        @Override
        public Flux<ChatResponse> stream(
                List<Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            String text = messages.isEmpty() ? "" : messages.get(messages.size() - 1).getTextContent();
            ContentBlock content = TextBlock.builder().text("echo:" + text).build();
            return Flux.just(ChatResponse.builder()
                    .content(List.of(content))
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "echo-model";
        }
    }
}
