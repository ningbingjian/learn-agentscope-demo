package com.example.agentscope.messageevent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MathTools {

    @Tool(
            name = "add_numbers",
            description = "Adds two integers and returns the exact sum.",
            strict = true,
            readOnly = true,
            concurrencySafe = true
    )
    public long add(
            @ToolParam(name = "left", description = "Left integer") long left,
            @ToolParam(name = "right", description = "Right integer") long right
    ) {
        return Math.addExact(left, right);
    }
}
