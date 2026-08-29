package com.example.agentscope.evaluation.model;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;

/** Deterministic model used to make the evaluation suite reproducible. */
public final class EvaluationModel implements Model {
    private final AtomicInteger inputTokens = new AtomicInteger();
    private final AtomicInteger outputTokens = new AtomicInteger();

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        ToolResultBlock toolResult = latestToolResult(messages);
        if (toolResult != null) {
            String output = toolResult.getOutput().stream()
                    .filter(TextBlock.class::isInstance)
                    .map(TextBlock.class::cast)
                    .map(TextBlock::getText)
                    .reduce((a, b) -> a + b)
                    .orElse("unknown");
            return response(List.of(TextBlock.builder().text("weather completed: " + output).build()), "stop", 14, 7);
        }

        String user = latestUserText(messages);
        if (user.contains("weather")) {
            String city = user.contains("Beijing") ? "Beijing" : user.contains("Shanghai") ? "Shanghai" : "unknown";
            ToolUseBlock call = ToolUseBlock.builder()
                    .id("weather-" + city)
                    .name("get_weather")
                    .input(Map.of("city", city))
                    .build();
            return response(List.of(call), "tool_calls", 18, 5);
        }
        return response(List.of(TextBlock.builder().text("hello from evaluated agent").build()), "stop", 10, 5);
    }

    private Flux<ChatResponse> response(List<io.agentscope.core.message.ContentBlock> content, String reason, int in, int out) {
        inputTokens.addAndGet(in);
        outputTokens.addAndGet(out);
        return Flux.just(ChatResponse.builder()
                .content(content)
                .usage(ChatUsage.builder().inputTokens(in).outputTokens(out).time(0.01).build())
                .finishReason(reason)
                .build());
    }

    private static String latestUserText(List<Msg> messages) {
        if (messages == null) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == MsgRole.USER) return messages.get(i).getTextContent();
        }
        return "";
    }

    private static ToolResultBlock latestToolResult(List<Msg> messages) {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            List<ToolResultBlock> results = messages.get(i).getContentBlocks(ToolResultBlock.class);
            if (!results.isEmpty()) return results.get(results.size() - 1);
        }
        return null;
    }

    public void resetUsage() { inputTokens.set(0); outputTokens.set(0); }
    public int inputTokens() { return inputTokens.get(); }
    public int outputTokens() { return outputTokens.get(); }

    @Override
    public String getModelName() { return "lesson53-evaluation-model"; }
}
