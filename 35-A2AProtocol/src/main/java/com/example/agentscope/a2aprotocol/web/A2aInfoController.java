package com.example.agentscope.a2aprotocol.web;

import io.a2a.spec.AgentCard;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/a2a")
public class A2aInfoController {

    private final AgentScopeA2aServer server;

    public A2aInfoController(AgentScopeA2aServer server) {
        this.server = server;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        AgentCard card = server.getAgentCard();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol", "A2A");
        result.put("name", card.name());
        result.put("description", card.description());
        result.put("url", card.url());
        result.put("version", card.version());
        result.put("agentCard", "/.well-known/agent-card.json");
        result.put("jsonRpcEndpoint", "/");
        return result;
    }
}
