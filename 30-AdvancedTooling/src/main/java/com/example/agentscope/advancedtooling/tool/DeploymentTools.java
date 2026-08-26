package com.example.agentscope.advancedtooling.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DeploymentTools {

    @Tool(
            name = "deployment_preview",
            description = "Returns a read-only simulated deployment plan for a service.",
            strict = true,
            readOnly = true,
            concurrencySafe = true
    )
    public String preview(
            @ToolParam(name = "service", description = "Service name") String service
    ) {
        return "SIMULATED_DEPLOYMENT service=" + service + ", strategy=rolling-update";
    }
}
