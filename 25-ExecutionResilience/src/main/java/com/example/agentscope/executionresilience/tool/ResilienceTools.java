package com.example.agentscope.executionresilience.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ResilienceTools {

    @Tool(
            name = "slow_task",
            description = "Sleeps for the requested milliseconds, then returns a completion message.",
            strict = true,
            readOnly = true,
            concurrencySafe = true
    )
    public String slowTask(
            @ToolParam(name = "millis", description = "Sleep duration from 1 to 10000 milliseconds")
            long millis
    ) {
        if (millis < 1 || millis > 10_000) {
            throw new IllegalArgumentException("millis must be between 1 and 10000");
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("slow_task interrupted", exception);
        }
        return "slow task completed after " + millis + " ms";
    }
}
