package com.example.agentscope.middlewarelifecycle;

import com.example.agentscope.middlewarelifecycle.middleware.AgentExecutionLoggingMiddleware;
import io.agentscope.core.agent.RuntimeContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExecutionLoggingMiddlewareTest {

    @Test
    void recordsAllFiveLifecycleEntryPoints() {
        AgentExecutionLoggingMiddleware middleware = new AgentExecutionLoggingMiddleware();
        RuntimeContext context = RuntimeContext.builder()
                .userId("alice")
                .sessionId("middleware-test")
                .build();

        middleware.onAgent(null, context, null, ignored -> Flux.empty()).blockLast();
        middleware.onReasoning(null, context, null, ignored -> Flux.empty()).blockLast();
        middleware.onActing(null, context, null, ignored -> Flux.empty()).blockLast();
        middleware.onModelCall(null, context, null, ignored -> Flux.empty()).blockLast();
        String prompt = middleware.onSystemPrompt(null, context, "base prompt").block();

        AgentExecutionLoggingMiddleware.LifecycleSnapshot snapshot = middleware.snapshot();

        assertThat(prompt).isEqualTo("base prompt");
        assertThat(snapshot.agentCalls()).isEqualTo(1);
        assertThat(snapshot.reasoningCalls()).isEqualTo(1);
        assertThat(snapshot.actingCalls()).isEqualTo(1);
        assertThat(snapshot.modelCalls()).isEqualTo(1);
        assertThat(snapshot.systemPromptTransforms()).isEqualTo(1);
    }
}
