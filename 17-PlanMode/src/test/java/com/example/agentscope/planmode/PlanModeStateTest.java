package com.example.agentscope.planmode;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanModeStateTest {

    @TempDir
    Path workspace;

    @Test
    void programmaticEnterAndExitAreSessionScoped() throws Exception {
        Files.writeString(workspace.resolve("AGENTS.md"), "# test planner");

        try (HarnessAgent agent = HarnessAgent.builder()
                .name("test-planner")
                .model(new NoopModel())
                .workspace(workspace)
                .enablePlanMode()
                .planFileDirectory("plans")
                .disableMemoryHooks()
                .disableCompaction()
                .build()) {

            RuntimeContext alice = RuntimeContext.builder()
                    .userId("alice")
                    .sessionId("s1")
                    .build();
            RuntimeContext bob = RuntimeContext.builder()
                    .userId("bob")
                    .sessionId("s2")
                    .build();

            assertThat(agent.isPlanModeActive(alice)).isFalse();
            assertThat(agent.isPlanModeActive(bob)).isFalse();

            agent.enterPlanMode(alice);

            assertThat(agent.isPlanModeActive(alice)).isTrue();
            assertThat(agent.isPlanModeActive(bob)).isFalse();

            agent.exitPlanMode(alice);
            assertThat(agent.isPlanModeActive(alice)).isFalse();
        }
    }

    private static final class NoopModel implements Model {
        @Override
        public Flux<ChatResponse> stream(
                List<Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options) {
            ContentBlock content = TextBlock.builder().text("noop").build();
            return Flux.just(ChatResponse.builder()
                    .content(List.of(content))
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "noop-model";
        }
    }
}
