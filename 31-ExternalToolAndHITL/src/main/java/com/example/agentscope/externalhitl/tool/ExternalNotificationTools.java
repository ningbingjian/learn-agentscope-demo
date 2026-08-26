package com.example.agentscope.externalhitl.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExternalNotificationTools {

    private final AtomicInteger bodyExecutions = new AtomicInteger();

    @Tool(
            name = "external_send_notification",
            description = "Send a notification through an external operator/system. The framework must suspend and wait for an external result.",
            externalTool = true,
            readOnly = false,
            concurrencySafe = true)
    public String sendNotification(
            @ToolParam(name = "channel", description = "Target channel") String channel,
            @ToolParam(name = "message", description = "Notification body") String message) {
        bodyExecutions.incrementAndGet();
        return "SHOULD_NOT_RUN_INSIDE_AGENT";
    }

    public int bodyExecutions() {
        return bodyExecutions.get();
    }
}
