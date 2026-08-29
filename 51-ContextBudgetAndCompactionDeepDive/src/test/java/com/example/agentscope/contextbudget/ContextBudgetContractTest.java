package com.example.agentscope.contextbudget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agentscope.contextbudget.service.ContextBudgetService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystem;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.middleware.ToolResultEvictionMiddleware;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import io.agentscope.harness.agent.workspace.PathPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

class ContextBudgetContractTest {
    @TempDir Path tempDir;
    private final ContextBudgetService service = new ContextBudgetService();

    @Test
    void exampleBudgetExactlyFitsA128kWindow() {
        Map<String, Object> plan = service.plannedBudget();
        assertThat(plan.get("contextWindow")).isEqualTo(128_000);
        assertThat(plan.get("used")).isEqualTo(128_000);
        assertThat(plan.get("remaining")).isEqualTo(0);
    }

    @Test
    void dynamicCompactionUsesRealTwoDotZeroOneDefaults() {
        Map<String, Object> known = service.dynamicCompaction(128_000);
        assertThat(known.get("effectiveTriggerTokens")).isEqualTo(108_000);
        assertThat(known.get("effectiveKeepTokens")).isEqualTo(8_000);
        assertThat(known.get("keepMessagesFallback")).isEqualTo(20);
        assertThat(known.get("flushBeforeCompact")).isEqualTo(true);
        assertThat(known.get("offloadBeforeCompact")).isEqualTo(true);
        assertThat(known.get("pruneEnabled")).isEqualTo(true);

        Map<String, Object> unknown = service.dynamicCompaction(0);
        assertThat(unknown.get("effectiveTriggerTokens")).isEqualTo(CompactionConfig.FALLBACK_TRIGGER_TOKENS);
        assertThat(unknown.get("effectiveKeepTokens")).isEqualTo(0);
    }

    @Test
    void defaultEvictionTargetsOversizedIndividualToolResults() {
        ToolResultEvictionConfig defaults = ToolResultEvictionConfig.defaults();
        assertThat(defaults.getMaxResultChars()).isEqualTo(80_000);
        assertThat(defaults.getPreviewChars()).isEqualTo(2_000);
        assertThat(defaults.getEvictionPath()).isEqualTo("large_tool_results");
        assertThat(defaults.getExcludedToolNames()).contains("read_file", "memory_search");
    }

    @Test
    void oversizedToolResultIsReallyEvictedToFilesystemBeforeReasoning() throws Exception {
        String fullOutput = "0123456789".repeat(500); // 5,000 chars
        ToolResultEvictionConfig config = ToolResultEvictionConfig.builder()
                .maxResultChars(1_000)
                .previewChars(100)
                .build();
        LocalFilesystem filesystem = new LocalFilesystem(
                tempDir, LocalFsMode.ROOTED, PathPolicy.empty(), 10, null);
        ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(filesystem, config);

        ToolResultBlock original = new ToolResultBlock(
                "call:huge-1",
                "huge_output",
                List.of(TextBlock.builder().text(fullOutput).build()),
                Map.of("source", "lesson"),
                ToolResultState.SUCCESS);
        Msg toolMessage = Msg.builder().role(MsgRole.TOOL).content(original).build();
        AgentState state = AgentState.builder().addMessage(toolMessage).build();
        RuntimeContext ctx = RuntimeContext.builder().agentState(state).build();
        ReasoningInput input = new ReasoningInput(List.of(toolMessage), List.of(), null);
        AtomicReference<ReasoningInput> forwarded = new AtomicReference<>();

        Agent agent = mock(Agent.class);
        when(agent.getName()).thenReturn("Context Agent");

        middleware.onReasoning(agent, ctx, input, compacted -> {
                    forwarded.set(compacted);
                    return Flux.empty();
                })
                .collectList()
                .block();

        String modelVisible = toolOutput(forwarded.get().messages().get(0));
        String stateVisible = toolOutput(state.getContext().get(0));
        assertThat(modelVisible).contains("Tool output was too large");
        assertThat(modelVisible).doesNotContain(fullOutput);
        assertThat(stateVisible).contains("Tool output was too large");
        assertThat(stateVisible).doesNotContain(fullOutput);

        Path dir = tempDir.resolve("large_tool_results/Context_Agent");
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.toList();
        }
        assertThat(files).hasSize(1);
        assertThat(Files.readString(files.get(0))).isEqualTo(fullOutput);
    }

    private static String toolOutput(Msg message) {
        ToolResultBlock result = message.getContentBlocks(ToolResultBlock.class).get(0);
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : result.getOutput()) {
            if (block instanceof TextBlock tb && tb.getText() != null) {
                text.append(tb.getText());
            }
        }
        return text.toString();
    }
}
