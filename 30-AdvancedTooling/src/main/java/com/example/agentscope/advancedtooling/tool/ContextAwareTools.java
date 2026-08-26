package com.example.agentscope.advancedtooling.tool;

import com.example.agentscope.advancedtooling.domain.RequestProfile;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ContextAwareTools {

    @Tool(
            name = "who_am_i",
            description = "Returns the current request identity and tenant profile.",
            strict = true,
            readOnly = true,
            concurrencySafe = true
    )
    public String whoAmI(
            @ToolParam(name = "prefix", description = "Prefix shown before the identity") String prefix,
            RuntimeContext context,
            RequestProfile profile
    ) {
        String tenant = profile == null ? "unknown" : profile.tenant();
        String locale = profile == null ? "unknown" : profile.locale();
        String userId = context == null ? null : context.getUserId();
        String sessionId = context == null ? null : context.getSessionId();
        return prefix
                + " userId=" + userId
                + ", sessionId=" + sessionId
                + ", tenant=" + tenant
                + ", locale=" + locale;
    }
}
