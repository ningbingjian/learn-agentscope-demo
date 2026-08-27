package com.example.agentscope.asynctool.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class SlowReportTools {
    private final AtomicInteger executions = new AtomicInteger();

    @Tool(name = "slow_report", description = "Generate a simulated report that takes about 250ms.")
    public String slowReport(@ToolParam(name = "topic", description = "Report topic") String topic) {
        executions.incrementAndGet();
        try {
            Thread.sleep(250L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("slow report interrupted", e);
        }
        return "report-ready:" + topic;
    }

    public int executions() { return executions.get(); }
    public void reset() { executions.set(0); }
}
