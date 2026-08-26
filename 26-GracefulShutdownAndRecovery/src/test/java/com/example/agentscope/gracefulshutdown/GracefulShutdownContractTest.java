package com.example.agentscope.gracefulshutdown;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.shutdown.AgentShuttingDownException;
import io.agentscope.core.shutdown.GracefulShutdownConfig;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.shutdown.PartialReasoningPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GracefulShutdownContractTest {

    private final GracefulShutdownManager manager = GracefulShutdownManager.getInstance();

    @BeforeEach
    void resetBefore() {
        manager.resetForTesting();
        manager.setConfig(new GracefulShutdownConfig(
                Duration.ofSeconds(2),
                PartialReasoningPolicy.SAVE
        ));
    }

    @AfterEach
    void resetAfter() {
        manager.resetForTesting();
    }

    @Test
    void drainStopsNewAgentCalls() {
        try (ReActAgent agent = ReActAgent.builder()
                .name("shutdown-test-agent")
                .sysPrompt("test")
                .model(new ImmediateModel())
                .build()) {
            assertThat(agent.call("before drain").block()).isNotNull();
            assertThat(manager.isAcceptingRequests()).isTrue();

            assertThat(manager.performGracefulShutdown()).isTrue();
            assertThat(manager.isAcceptingRequests()).isFalse();

            assertThatThrownBy(() -> agent.call("after drain").block())
                    .hasRootCauseInstanceOf(AgentShuttingDownException.class);
        }
    }

    private static final class ImmediateModel implements Model {

        @Override
        public Flux<ChatResponse> stream(
                List<io.agentscope.core.message.Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            ContentBlock content = io.agentscope.core.message.TextBlock.builder()
                    .text("ok")
                    .build();
            return Flux.just(ChatResponse.builder()
                    .content(List.of(content))
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "immediate-model";
        }
    }
}
