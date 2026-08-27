package com.example.agentscope.adminops.tool;

import io.agentscope.core.tool.Tool;

public final class AdminDemoTools {

    @Tool(
            name = "system_info",
            description = "Return a small deterministic system summary for the admin lesson.",
            readOnly = true)
    public String systemInfo() {
        return "lesson-service=healthy";
    }
}
