package com.example.agentscope.observabilityandtracing.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticTools {

    @Tool(
            name = "service_status",
            description = "Returns a simulated health status for a named service component.",
            strict = true,
            readOnly = true,
            concurrencySafe = true
    )
    public String serviceStatus(
            @ToolParam(name = "component", description = "Service or component name")
            String component
    ) {
        if (component == null || component.isBlank()) {
            throw new IllegalArgumentException("component must not be blank");
        }
        return component + " status=UP, latencyMs=23";
    }
}
