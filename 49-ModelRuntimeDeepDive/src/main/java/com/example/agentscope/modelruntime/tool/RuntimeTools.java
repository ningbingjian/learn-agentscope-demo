package com.example.agentscope.modelruntime.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

public final class RuntimeTools {
    @Tool(name = "runtime_probe", description = "Read a deterministic runtime probe value", readOnly = true)
    public String probe(@ToolParam(name = "value", description = "Probe value") String value) {
        return "probe:" + value;
    }
}
