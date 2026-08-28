package com.example.agentscope.asynctool.model;

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

public final class AsyncToolDemoModel implements Model {
    private final AtomicInteger toolCalls = new AtomicInteger();

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        String joined = messages == null ? "" : messages.stream()
                .map(Msg::getTextContent)
                .filter(text -> text != null && !text.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        if (joined.contains("running in background has completed")) {
            return text("后台 slow_report 已完成，我已经收到真实结果。", "stop");
        }

        ToolResultBlock latest = latestToolResult(messages);
        if (latest != null) {
            String output = latest.getOutput().stream()
                    .filter(TextBlock.class::isInstance)
                    .map(TextBlock.class::cast)
                    .map(TextBlock::getText)
                    .reduce((a, b) -> a + b)
                    .orElse("");
            if (output.contains("running in background")) {
                return text("slow_report 已转入后台执行，我可以先继续处理其他工作。", "stop");
            }
            return text("slow_report 已完成：" + output, "stop");
        }

        String request = messages == null || messages.isEmpty()
                ? "AgentScope async tool"
                : messages.get(messages.size() - 1).getTextContent();
        ToolUseBlock call = ToolUseBlock.builder()
                .id("slow-call-" + toolCalls.incrementAndGet())
                .name("slow_report")
                .input(Map.of("topic", request == null || request.isBlank() ? "AgentScope" : request))
                .build();
        return Flux.just(ChatResponse.builder().content(List.of(call)).finishReason("tool_calls").build());
    }

    private static ToolResultBlock latestToolResult(List<Msg> messages) {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            List<ToolResultBlock> blocks = messages.get(i).getContentBlocks(ToolResultBlock.class);
            for (ToolResultBlock block : blocks) {
                if ("slow_report".equals(block.getName())) return block;
            }
        }
        return null;
    }

    private static Flux<ChatResponse> text(String value, String finishReason) {
        ContentBlock block = TextBlock.builder().text(value).build();
        return Flux.just(ChatResponse.builder().content(List.of(block)).finishReason(finishReason).build());
    }

    @Override
    public String getModelName() { return "lesson-async-tool-model"; }
}
