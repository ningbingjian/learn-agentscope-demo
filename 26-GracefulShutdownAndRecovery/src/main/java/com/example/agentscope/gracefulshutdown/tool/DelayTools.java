package com.example.agentscope.gracefulshutdown.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DelayTools {

    @Tool(
            name = "pause",
            description = "Waits for 1 to 30 seconds and then returns normally.",
            strict = true,
            readOnly = true,
            concurrencySafe = true
    )
    public String pause(
            @ToolParam(name = "seconds", description = "Seconds to wait, from 1 to 30") int seconds
    ) {
        if (seconds < 1 || seconds > 30) {
            throw new IllegalArgumentException("seconds must be between 1 and 30");
        }
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("pause thread interrupted", exception);
        }
        return "waited " + seconds + " second(s)";
    }
}
