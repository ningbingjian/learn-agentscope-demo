package com.example.agentscope.agentinterrupt;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionInterruptTest {

    @TempDir
    Path tempDir;

    @Test
    void targetsOnlyTheRequestedUserAndSession() {
        try (HarnessAgent agent = newAgent(new ProbeModel(), tempDir)) {
            RuntimeContext alice = context("alice", "session-1");
            RuntimeContext bob = context("bob", "session-1");

            AgentState aliceState = agent.getDelegate().getAgentState(alice);
            AgentState bobState = agent.getDelegate().getAgentState(bob);

            agent.getDelegate().interrupt(alice);

            assertThat(aliceState.interruptControl().isInterrupted()).isTrue();
            assertThat(bobState.interruptControl().isInterrupted()).isFalse();
        }
    }

    @Test
    void keepsTheInterruptMessageOnTheTargetSession() {
        try (HarnessAgent agent = newAgent(new ProbeModel(), tempDir)) {
            RuntimeContext alice = context("alice", "session-1");
            UserMessage interruptMessage = new UserMessage("用户主动取消了当前任务。");

            AgentState aliceState = agent.getDelegate().getAgentState(alice);
            agent.getDelegate().interrupt(alice, interruptMessage);

            assertThat(aliceState.interruptControl().isInterrupted()).isTrue();
            assertThat(aliceState.interruptControl().getUserMessage())
                    .isSameAs(interruptMessage);
        }
    }

    private static HarnessAgent newAgent(Model model, Path workspace) {
        return HarnessAgent.builder()
                .name("interrupt-test-agent")
                .sysPrompt("Reply briefly.")
                .model(model)
                .stateStore(new InMemoryAgentStateStore())
                .workspace(workspace)
                .build();
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
    }

    private static final class ProbeModel implements Model {

        @Override
        public Flux<ChatResponse> stream(
                List<io.agentscope.core.message.Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            ContentBlock content = TextBlock.builder().text("ok").build();
            ChatResponse response = ChatResponse.builder()
                    .content(List.of(content))
                    .finishReason("stop")
                    .build();
            return Flux.just(response);
        }

        @Override
        public String getModelName() {
            return "probe-model";
        }
    }
}
