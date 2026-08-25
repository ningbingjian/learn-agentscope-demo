package com.example.agentscope.multiuserconcurrency.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class DelayTools {

    private static final Logger log = LoggerFactory.getLogger(DelayTools.class);

    @Tool(
            name = "pause",
            description = "Waits for the requested number of seconds, from 1 to 10, "
                    + "and then returns a completion message.",
            strict = true,
            readOnly = true,
            concurrencySafe = true
    )
    public String pause(
            @ToolParam(name = "seconds", description = "Number of seconds to wait, from 1 to 10")
            int seconds
    ) {
        if (seconds < 1 || seconds > 10) {
            throw new IllegalArgumentException("seconds must be between 1 and 10");
        }

        log.info("pause tool started: seconds={}", seconds);
        try {
            Thread.sleep(Duration.ofSeconds(seconds).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("pause tool was interrupted", exception);
        }
        log.info("pause tool completed: seconds={}", seconds);
        return "waited " + seconds + " second(s)";
    }
}
