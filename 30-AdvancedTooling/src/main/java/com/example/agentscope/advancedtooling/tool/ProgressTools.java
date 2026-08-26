package com.example.agentscope.advancedtooling.tool;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolEmitter;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ProgressTools {

    @Tool(
            name = "progress_task",
            description = "Runs a simulated multi-step task and emits progress chunks.",
            strict = true,
            readOnly = true,
            concurrencySafe = true
    )
    public ToolResultBlock run(
            @ToolParam(name = "task", description = "Short task description") String task,
            ToolEmitter emitter
    ) {
        emitter.emit(ToolResultBlock.text("progress 25%: accepted " + task));
        emitter.emit(ToolResultBlock.text("progress 75%: processing " + task));
        return ToolResultBlock.text("completed: " + task);
    }
}
