package com.example.agentscope.modelruntime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.modelruntime.formatter.DemoFormatter;
import com.example.agentscope.modelruntime.model.RuntimeInspectionModel;
import com.example.agentscope.modelruntime.tool.RuntimeTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.AudioBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.message.VideoBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelRuntimeContractTest {

    @Test
    void generateOptionsMergePerRequestOverDefaults() {
        GenerateOptions defaults = GenerateOptions.builder()
                .temperature(0.7).parallelToolCalls(true).cacheControl(true).maxTokens(4096).build();
        GenerateOptions request = GenerateOptions.builder().temperature(0.2).thinkingBudget(2048).build();
        GenerateOptions merged = GenerateOptions.mergeOptions(request, defaults);
        assertThat(merged.getTemperature()).isEqualTo(0.2);
        assertThat(merged.getThinkingBudget()).isEqualTo(2048);
        assertThat(merged.getParallelToolCalls()).isTrue();
        assertThat(merged.getCacheControl()).isTrue();
        assertThat(merged.getMaxTokens()).isEqualTo(4096);
    }

    @Test
    void formatterOwnsProviderTranslationAndOptionApplication() {
        DemoFormatter formatter = new DemoFormatter();
        DemoFormatter.DemoParams params = new DemoFormatter.DemoParams();
        formatter.applyOptions(
                params,
                GenerateOptions.builder().temperature(0.1).thinkingBudget(1024).build(),
                GenerateOptions.builder().parallelToolCalls(false).cacheControl(true).build());
        assertThat(formatter.format(List.of(new UserMessage("hello"))))
                .containsExactly("USER:hello");
        assertThat(params.temperature()).isEqualTo(0.1);
        assertThat(params.thinkingBudget()).isEqualTo(1024);
        assertThat(params.parallelToolCalls()).isFalse();
        assertThat(params.cacheControl()).isTrue();
    }

    @Test
    void reactAgentPassesGenerateOptionsAndToolSchemasToModel() {
        RuntimeInspectionModel model = new RuntimeInspectionModel();
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new RuntimeTools());
        GenerateOptions options = GenerateOptions.builder()
                .temperature(0.2).thinkingBudget(1536).parallelToolCalls(false).cacheControl(true).build();
        try (ReActAgent agent = ReActAgent.builder()
                .name("runtime-test-agent")
                .sysPrompt("test")
                .model(model)
                .toolkit(toolkit)
                .generateOptions(options)
                .build()) {
            var reply = agent.call(new UserMessage("inspect runtime")).block();
            assertThat(model.lastOptions().getThinkingBudget()).isEqualTo(1536);
            assertThat(model.lastOptions().getCacheControl()).isTrue();
            assertThat(model.lastToolCount()).isEqualTo(1);
            assertThat(reply.getContentBlocks(ThinkingBlock.class)).hasSize(1);
            assertThat(reply.getContentBlocks(TextBlock.class).get(0).getText()).isEqualTo("runtime-ok");
        }
    }

    @Test
    void multimodalBlocksAreUnifiedBeforeProviderFormatting() {
        var source = new URLSource("https://example.invalid/media", "application/octet-stream");
        assertThat(List.of(
                TextBlock.builder().text("text").build(),
                ImageBlock.builder().source(source).build(),
                AudioBlock.builder().source(source).build(),
                VideoBlock.builder().source(source).fps(2.0f).build()))
                .extracting(v -> v.getClass().getSimpleName())
                .containsExactly("TextBlock", "ImageBlock", "AudioBlock", "VideoBlock");
    }
}
