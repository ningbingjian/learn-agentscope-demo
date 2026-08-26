package com.example.agentscope.executionresilience;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionResilienceTest {

    @Test
    void agentPassesModelExecutionConfigAndRetryCanRecoverTransientFailure() {
        AtomicInteger attempts = new AtomicInteger();
        Model flakyModel = new FlakyModel(attempts);
        ExecutionConfig modelPolicy = ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(2))
                .maxAttempts(3)
                .initialBackoff(Duration.ofMillis(10))
                .maxBackoff(Duration.ofMillis(30))
                .backoffMultiplier(2.0)
                .retryOn(error -> true)
                .build();
        ExecutionConfig toolPolicy = ExecutionConfig.builder()
                .timeout(Duration.ofMillis(100))
                .maxAttempts(1)
                .build();

        try (ReActAgent agent = ReActAgent.builder()
                .name("resilience-test-agent")
                .sysPrompt("test")
                .model(flakyModel)
                .modelExecutionConfig(modelPolicy)
                .toolExecutionConfig(toolPolicy)
                .build()) {
            var reply = agent.call(new UserMessage("hello")).block();

            assertThat(reply).isNotNull();
            assertThat(reply.getTextContent()).contains("recovered");
            assertThat(attempts.get()).isEqualTo(3);
            assertThat(agent.getModelExecutionConfig().getMaxAttempts()).isEqualTo(3);
            assertThat(agent.getToolExecutionConfig().getMaxAttempts()).isEqualTo(1);
        }
    }

    private static final class FlakyModel implements Model {

        private final AtomicInteger attempts;

        private FlakyModel(AtomicInteger attempts) {
            this.attempts = attempts;
        }

        @Override
        public Flux<ChatResponse> stream(
                List<io.agentscope.core.message.Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            Flux<ChatResponse> response = Flux.defer(() -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    return Flux.error(new RuntimeException("transient failure " + attempt));
                }
                ContentBlock content = io.agentscope.core.message.TextBlock.builder()
                        .text("recovered on attempt " + attempt)
                        .build();
                return Flux.just(ChatResponse.builder()
                        .content(List.of(content))
                        .finishReason("stop")
                        .build());
            });

            ExecutionConfig config = options == null ? null : options.getExecutionConfig();
            if (config == null || config.getMaxAttempts() == null || config.getMaxAttempts() <= 1) {
                return response;
            }
            return response.retryWhen(
                    Retry.backoff(config.getMaxAttempts() - 1, config.getInitialBackoff())
                            .maxBackoff(config.getMaxBackoff())
                            .jitter(0)
                            .filter(config.getRetryOn())
            );
        }

        @Override
        public String getModelName() {
            return "flaky-model";
        }
    }
}
