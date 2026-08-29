package com.example.agentscope.modelruntime.formatter;

import io.agentscope.core.formatter.Formatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import java.time.Instant;
import java.util.List;

/**
 * Tiny provider formatter used to make the Formatter responsibility visible without calling a real SDK.
 */
public final class DemoFormatter implements Formatter<String, String, DemoFormatter.DemoParams> {

    @Override
    public List<String> format(List<Msg> msgs) {
        return msgs.stream().map(msg -> msg.getRole().name() + ":" + msg.getTextContent()).toList();
    }

    @Override
    public ChatResponse parseResponse(String response, Instant startTime) {
        return ChatResponse.builder()
                .content(List.of(TextBlock.builder().text(response).build()))
                .finishReason("stop")
                .build();
    }

    @Override
    public void applyOptions(DemoParams params, GenerateOptions options, GenerateOptions defaults) {
        GenerateOptions merged = GenerateOptions.mergeOptions(options, defaults);
        if (merged == null) {
            return;
        }
        params.temperature = merged.getTemperature();
        params.thinkingBudget = merged.getThinkingBudget();
        params.parallelToolCalls = merged.getParallelToolCalls();
        params.cacheControl = merged.getCacheControl();
    }

    @Override
    public void applyTools(DemoParams params, List<ToolSchema> tools) {
        params.toolCount = tools == null ? 0 : tools.size();
    }

    public static final class DemoParams {
        private Double temperature;
        private Integer thinkingBudget;
        private Boolean parallelToolCalls;
        private Boolean cacheControl;
        private int toolCount;

        public Double temperature() { return temperature; }
        public Integer thinkingBudget() { return thinkingBudget; }
        public Boolean parallelToolCalls() { return parallelToolCalls; }
        public Boolean cacheControl() { return cacheControl; }
        public int toolCount() { return toolCount; }
    }
}
