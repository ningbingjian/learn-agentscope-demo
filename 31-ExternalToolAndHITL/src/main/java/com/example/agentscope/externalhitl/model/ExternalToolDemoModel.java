package com.example.agentscope.externalhitl.model;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;

/** Deterministic model used to make the external-tool suspend/resume flow reproducible. */
public final class ExternalToolDemoModel implements Model {

    private final AtomicInteger callIds = new AtomicInteger();

    @Override
    public Flux<ChatResponse> stream(
            List<Msg> messages,
            List<ToolSchema> tools,
            GenerateOptions options) {
        ToolResultBlock result = latestExternalResult(messages);
        if (result != null) {
            String output = result.getOutput().stream()
                    .filter(TextBlock.class::isInstance)
                    .map(TextBlock.class::cast)
                    .map(TextBlock::getText)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("<empty>");
            return textResponse("已收到外部系统执行结果：" + output);
        }

        String requestText = messages == null || messages.isEmpty()
                ? "请发送学习通知"
                : messages.get(messages.size() - 1).getTextContent();
        ToolUseBlock call = ToolUseBlock.builder()
                .id("external-call-" + callIds.incrementAndGet())
                .name("external_send_notification")
                .input(Map.of(
                        "channel", "ops-demo",
                        "message", requestText.isBlank() ? "AgentScope 外部执行演示" : requestText))
                .build();
        return Flux.just(ChatResponse.builder()
                .content(List.of(call))
                .finishReason("tool_calls")
                .build());
    }

    private static Flux<ChatResponse> textResponse(String text) {
        ContentBlock block = TextBlock.builder().text(text).build();
        return Flux.just(ChatResponse.builder()
                .content(List.of(block))
                .finishReason("stop")
                .build());
    }

    private static ToolResultBlock latestExternalResult(List<Msg> messages) {
        if (messages == null) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            for (ToolResultBlock result : messages.get(i).getContentBlocks(ToolResultBlock.class)) {
                if ("external_send_notification".equals(result.getName())) {
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    public String getModelName() {
        return "lesson-external-tool-model";
    }
}
