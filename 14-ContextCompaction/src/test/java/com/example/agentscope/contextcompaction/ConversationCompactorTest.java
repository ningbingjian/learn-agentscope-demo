package com.example.agentscope.contextcompaction;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ConversationCompactor;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationCompactorTest {

    @Test
    void replacesOldPrefixWithSummaryAndKeepsRecentTail() {
        ConversationCompactor compactor = new ConversationCompactor(new SummaryModel(), null);
        CompactionConfig config = CompactionConfig.builder()
                .triggerMessages(4)
                .triggerTokens(Integer.MAX_VALUE)
                .keepMessages(2)
                .keepTokens(0)
                .flushBeforeCompact(false)
                .offloadBeforeCompact(false)
                .prune(null)
                .build();

        List<Msg> messages = List.of(
                new UserMessage("turn-1 user"),
                new AssistantMessage("turn-1 assistant"),
                new UserMessage("turn-2 user"),
                new AssistantMessage("turn-2 assistant"),
                new UserMessage("turn-3 user"),
                new AssistantMessage("turn-3 assistant")
        );

        Optional<List<Msg>> result = compactor.compactIfNeeded(
                RuntimeContext.empty(), messages, config, "agent", "session"
        ).block();

        assertThat(result).isPresent();
        List<Msg> compacted = result.orElseThrow();
        assertThat(compacted).hasSize(3);
        assertThat(compacted.get(0).getName()).isEqualTo(ConversationCompactor.SUMMARY_MSG_NAME);
        assertThat(compacted.get(0).getTextContent()).contains("condensed summary");
        assertThat(compacted.get(1).getTextContent()).contains("turn-3 user");
        assertThat(compacted.get(2).getTextContent()).contains("turn-3 assistant");
    }

    private static final class SummaryModel implements Model {

        @Override
        public Flux<ChatResponse> stream(
                List<Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            ContentBlock content = TextBlock.builder().text("condensed summary").build();
            return Flux.just(ChatResponse.builder()
                    .content(List.of(content))
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "summary-model";
        }
    }
}
