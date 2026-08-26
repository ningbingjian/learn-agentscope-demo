package com.example.agentscope.agentobserve;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentObserveTest {

    @Test
    void observeAddsMessageWithoutCallingModel() {
        CountingModel model = new CountingModel();

        try (ReActAgent writer = ReActAgent.builder()
                .name("writer-agent")
                .defaultSessionId("writer-default")
                .sysPrompt("Write from observed context.")
                .model(model)
                .build()) {

            AssistantMessage research = AssistantMessage.builder()
                    .name("researcher-agent")
                    .textContent("关键事实：AgentScope Java 支持 observe。")
                    .build();

            writer.observe(research).block();

            assertThat(model.callCount).isZero();
            assertThat(writer.getAgentState(null, "writer-default").getContext())
                    .hasSize(1);
            assertThat(writer.getAgentState(null, "writer-default").getContext().get(0)
                    .getTextContent())
                    .contains("observe");
        }
    }

    private static final class CountingModel implements Model {

        private int callCount;

        @Override
        public Flux<ChatResponse> stream(
                List<io.agentscope.core.message.Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            callCount++;
            ContentBlock content = io.agentscope.core.message.TextBlock.builder()
                    .text("unused")
                    .build();
            return Flux.just(ChatResponse.builder()
                    .content(List.of(content))
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "counting-model";
        }
    }
}
