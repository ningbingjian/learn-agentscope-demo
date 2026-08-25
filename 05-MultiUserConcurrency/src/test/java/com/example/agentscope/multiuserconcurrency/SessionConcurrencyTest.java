package com.example.agentscope.multiuserconcurrency;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SessionConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void serializesCallsForTheSameUserAndSession() {
        ProbeModel model = new ProbeModel(Duration.ofMillis(150));
        CallLifecycleProbe probe = new CallLifecycleProbe();

        try (HarnessAgent agent = newAgent(model, probe, tempDir)) {
            runTogether(
                    agent,
                    context("alice", "session-1"),
                    context("alice", "session-1")
            );
        }

        assertThat(probe.maxConcurrentCalls()).isEqualTo(1);
    }

    @Test
    void runsDifferentSessionsInParallel() {
        ProbeModel model = new ProbeModel(Duration.ofMillis(150));
        CallLifecycleProbe probe = new CallLifecycleProbe();

        try (HarnessAgent agent = newAgent(model, probe, tempDir)) {
            runTogether(
                    agent,
                    context("alice", "session-1"),
                    context("alice", "session-2")
            );
        }

        assertThat(probe.maxConcurrentCalls()).isEqualTo(2);
    }

    @Test
    void treatsTheSameSessionIdForDifferentUsersAsDifferentSlots() {
        ProbeModel model = new ProbeModel(Duration.ofMillis(150));
        CallLifecycleProbe probe = new CallLifecycleProbe();

        try (HarnessAgent agent = newAgent(model, probe, tempDir)) {
            runTogether(
                    agent,
                    context("alice", "shared-name"),
                    context("bob", "shared-name")
            );
        }

        assertThat(probe.maxConcurrentCalls()).isEqualTo(2);
    }

    @SuppressWarnings("removal")
    private static HarnessAgent newAgent(Model model, Hook hook, Path workspace) {
        return HarnessAgent.builder()
                .name("concurrency-test-agent")
                .sysPrompt("Reply briefly.")
                .model(model)
                .stateStore(new InMemoryAgentStateStore())
                .workspace(workspace)
                .hook(hook)
                .build();
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
    }

    private static void runTogether(
            HarnessAgent agent,
            RuntimeContext firstContext,
            RuntimeContext secondContext
    ) {
        Mono.zip(
                        agent.call(new UserMessage("first"), firstContext),
                        agent.call(new UserMessage("second"), secondContext)
                )
                .block(Duration.ofSeconds(5));
    }

    private static final class ProbeModel implements Model {

        private final Duration delay;

        private ProbeModel(Duration delay) {
            this.delay = delay;
        }

        @Override
        public Flux<ChatResponse> stream(
                List<io.agentscope.core.message.Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            return Flux.defer(() -> {
                ContentBlock content = TextBlock.builder().text("ok").build();
                ChatResponse response = ChatResponse.builder()
                        .content(List.of(content))
                        .finishReason("stop")
                        .build();

                return Flux.just(response)
                        .delayElements(delay);
            });
        }

        @Override
        public String getModelName() {
            return "probe-model";
        }
    }

    @SuppressWarnings("removal")
    private static final class CallLifecycleProbe implements Hook {

        private final AtomicInteger activeCalls = new AtomicInteger();
        private final AtomicInteger maxConcurrentCalls = new AtomicInteger();

        @Override
        public <T extends HookEvent> Mono<T> onEvent(T event) {
            if (event instanceof PreCallEvent) {
                int current = activeCalls.incrementAndGet();
                maxConcurrentCalls.accumulateAndGet(current, Math::max);
            } else if (event instanceof PostCallEvent) {
                activeCalls.decrementAndGet();
            }
            return Mono.just(event);
        }

        private int maxConcurrentCalls() {
            return maxConcurrentCalls.get();
        }
    }
}
