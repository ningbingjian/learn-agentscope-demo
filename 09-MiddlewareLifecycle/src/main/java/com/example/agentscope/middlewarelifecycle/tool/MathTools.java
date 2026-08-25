package com.example.agentscope.middlewarelifecycle.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MathTools {

    @Tool(
            name = "multiply",
            description = "Multiplies two integers and returns the exact result.",
            strict = true,
            readOnly = true,
            concurrencySafe = true
    )
    public long multiply(
            @ToolParam(name = "left", description = "Left integer") long left,
            @ToolParam(name = "right", description = "Right integer") long right
    ) {
        return Math.multiplyExact(left, right);
    }
}
