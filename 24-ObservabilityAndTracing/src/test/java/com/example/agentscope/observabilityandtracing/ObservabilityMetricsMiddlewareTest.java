package com.example.agentscope.observabilityandtracing;

import com.example.agentscope.observabilityandtracing.middleware.ObservabilityMetricsMiddleware;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityMetricsMiddlewareTest {

    @Test
    void collectsAgentAndModelMetricsWithoutExternalCollector() {
        ObservabilityMetricsMiddleware metrics = new ObservabilityMetricsMiddleware();

        try (HarnessAgent agent = HarnessAgent.builder()
                .name("observable-test-agent")
                .model(new FakeModel())
                .workspace(Paths.get("target/observability-test-workspace"))
                .enableAgentTracingLog(false)
                .middleware(metrics)
                .middleware(new OtelTracingMiddleware())
                .disableMemoryHooks()
                .disableMemoryTools()
                .disableCompaction()
                .build()) {

            RuntimeContext context = RuntimeContext.builder()
                    .userId("alice")
                    .sessionId("trace-test")
                    .build();

            agent.call(new UserMessage("hello tracing"), context).block();

            ObservabilityMetricsMiddleware.Snapshot snapshot = metrics.snapshot();
            assertThat(snapshot.agentCalls()).isEqualTo(1);
            assertThat(snapshot.successfulCalls()).isEqualTo(1);
            assertThat(snapshot.failedCalls()).isZero();
            assertThat(snapshot.reasoningRounds()).isGreaterThanOrEqualTo(1);
            assertThat(snapshot.modelCalls()).isGreaterThanOrEqualTo(1);
            assertThat(snapshot.totalLatencyMillis()).isGreaterThanOrEqualTo(0.0);
        }
    }

    private static final class FakeModel implements Model {
        @Override
        public Flux<ChatResponse> stream(
                List<Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            ContentBlock content = TextBlock.builder().text("observed").build();
            return Flux.just(ChatResponse.builder()
                    .content(List.of(content))
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "fake-observable-model";
        }
    }
}
