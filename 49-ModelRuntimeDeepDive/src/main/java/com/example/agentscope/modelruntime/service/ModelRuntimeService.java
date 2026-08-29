package com.example.agentscope.modelruntime.service;

import com.example.agentscope.modelruntime.formatter.DemoFormatter;
import com.example.agentscope.modelruntime.model.RuntimeInspectionModel;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.AudioBlock;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.message.VideoBlock;
import io.agentscope.core.model.GenerateOptions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ModelRuntimeService {
    private final ReActAgent agent;
    private final RuntimeInspectionModel model;
    private final GenerateOptions configuredOptions;

    public ModelRuntimeService(
            ReActAgent agent, RuntimeInspectionModel model, GenerateOptions configuredOptions) {
        this.agent = agent;
        this.model = model;
        this.configuredOptions = configuredOptions;
    }

    public Map<String, Object> configuredOptions() {
        return Map.of(
                "temperature", configuredOptions.getTemperature(),
                "maxTokens", configuredOptions.getMaxTokens(),
                "thinkingBudget", configuredOptions.getThinkingBudget(),
                "parallelToolCalls", configuredOptions.getParallelToolCalls(),
                "cacheControl", configuredOptions.getCacheControl(),
                "seed", configuredOptions.getSeed(),
                "contextWindow", model.getContextWindowSize());
    }

    public Map<String, Object> call(String message) {
        var reply = agent.call(new UserMessage(message)).block();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reply", reply == null ? null : reply.getTextContent());
        out.put("modelToolSchemaCount", model.lastToolCount());
        out.put("modelInputBlockTypes", model.lastBlockTypes());
        out.put("modelSawThinkingBudget", model.lastOptions() == null ? null : model.lastOptions().getThinkingBudget());
        return out;
    }

    public Map<String, Object> multimodalSample() {
        List<ContentBlock> blocks = List.of(
                TextBlock.builder().text("Describe the attached media").build(),
                ImageBlock.builder().source(new URLSource("https://example.invalid/demo.png", "image/png")).build(),
                AudioBlock.builder().source(new URLSource("https://example.invalid/demo.mp3", "audio/mpeg")).build(),
                VideoBlock.builder().source(new URLSource("https://example.invalid/demo.mp4", "video/mp4")).fps(2.0f).maxFrames(16).build());
        return Map.of(
                "blockTypes", blocks.stream().map(b -> b.getClass().getSimpleName()).toList(),
                "note", "Only constructs AgentScope blocks; no URL is fetched in this lesson.");
    }

    public Map<String, Object> formatterDemo() {
        DemoFormatter formatter = new DemoFormatter();
        DemoFormatter.DemoParams params = new DemoFormatter.DemoParams();
        GenerateOptions defaults = GenerateOptions.builder()
                .temperature(0.7)
                .parallelToolCalls(true)
                .cacheControl(true)
                .build();
        GenerateOptions request = GenerateOptions.builder().temperature(0.1).thinkingBudget(2048).build();
        formatter.applyOptions(params, request, defaults);
        var parsed = formatter.parseResponse("provider-response", Instant.now());
        return Map.of(
                "formatted", formatter.format(List.of(new UserMessage("hello"))),
                "temperature", params.temperature(),
                "thinkingBudget", params.thinkingBudget(),
                "parallelToolCalls", params.parallelToolCalls(),
                "cacheControl", params.cacheControl(),
                "parsedText", parsed.getContent().stream()
                        .filter(TextBlock.class::isInstance)
                        .map(TextBlock.class::cast)
                        .map(TextBlock::getText)
                        .findFirst().orElse(""));
    }
}
