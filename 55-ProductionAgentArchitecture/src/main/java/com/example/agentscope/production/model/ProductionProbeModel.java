package com.example.agentscope.production.model;

import io.agentscope.core.message.ContentBlock;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import reactor.core.publisher.Flux;

/** Deterministic model: the final lesson stays fully offline and reproducible. */
public final class ProductionProbeModel implements Model {
    private static final Pattern ORDER_ID = Pattern.compile("\\bA\\d{3,}\\b", Pattern.CASE_INSENSITIVE);

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
            return response(
                    List.of(TextBlock.builder().text("order lookup completed: " + output).build()),
                    "stop",
                    24,
                    12);
        }

        String user = latestUserText(messages);
        Matcher matcher = ORDER_ID.matcher(user);
        if (matcher.find()) {
            String orderId = matcher.group().toUpperCase();
            ToolUseBlock call = ToolUseBlock.builder()
                    .id("order-" + orderId)
                    .name("get_order_status")
                    .input(Map.of("orderId", orderId))
                    .build();
            return response(List.of(call), "tool_calls", 28, 6);
        }

        return response(
                List.of(TextBlock.builder()
                        .text("production demo response: request processed with isolated session context")
                        .build()),
                "stop",
                20,
                11);
    }

    private static Flux<ChatResponse> response(
            List<ContentBlock> content, String finishReason, int inputTokens, int outputTokens) {
        return Flux.just(ChatResponse.builder()
                .content(content)
                .usage(ChatUsage.builder()
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .time(0.01)
                        .build())
                .finishReason(finishReason)
                .build());
    }

    private static String latestUserText(List<Msg> messages) {
        if (messages == null) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg msg = messages.get(i);
            if (msg.getRole() == MsgRole.USER) return msg.getTextContent();
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

    @Override
    public String getModelName() {
        return "lesson55-production-probe-model";
    }

    @Override
    public int getContextWindowSize() {
        return 128_000;
    }
}
