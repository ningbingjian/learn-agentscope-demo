package com.example.agentscope.advancedtooling.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DatabaseTools {

    @Tool(
            name = "db_lookup",
            description = "Looks up a simulated business record by id.",
            strict = true,
            readOnly = true,
            concurrencySafe = true
    )
    public String lookup(
            @ToolParam(name = "recordId", description = "Business record id") String recordId
    ) {
        return "SIMULATED_DB_RECORD id=" + recordId + ", status=ACTIVE";
    }
}
