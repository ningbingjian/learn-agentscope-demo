package com.example.agentscope.runtimeextension.service;

import com.example.agentscope.runtimeextension.middleware.CountingMiddleware;
import com.example.agentscope.runtimeextension.model.HookDemoModel;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.message.UserMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings("removal")
public class RuntimeExtensionService {
    private final ReActAgent agent;
    private final CountingMiddleware middleware;
    private final HookDemoModel model;

    public RuntimeExtensionService(ReActAgent agent, CountingMiddleware middleware, HookDemoModel model) {
        this.agent = agent;
        this.middleware = middleware;
        this.model = model;
    }

    public Map<String, Object> call(String message) {
        middleware.reset();
        var reply = agent.call(new UserMessage(message)).block();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reply", reply == null ? null : reply.getTextContent());
        out.put("middleware", middleware.snapshot());
        out.put("modelSystemPrompt", model.lastSystemPrompt());
        return out;
    }

    public Map<String, Object> contract() {
        Deprecated deprecated = Hook.class.getAnnotation(Deprecated.class);
        return Map.of(
                "hookDeprecated", deprecated != null,
                "hookForRemoval", deprecated != null && deprecated.forRemoval(),
                "hookSince", deprecated == null ? "" : deprecated.since(),
                "recommended", "MiddlewareBase",
                "systemHookSemantics", "copied into AgentBase instance at construction time");
    }
}
