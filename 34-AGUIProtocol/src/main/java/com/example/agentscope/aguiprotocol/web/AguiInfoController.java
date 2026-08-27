package com.example.agentscope.aguiprotocol.web;

import io.agentscope.core.agui.registry.AguiAgentRegistry;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agui")
public class AguiInfoController {

    private final AguiAgentRegistry registry;

    public AguiInfoController(AguiAgentRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "registeredAgents", registry.size(),
                "assistantRegistered", registry.hasAgent("assistant"),
                "defaultRegistered", registry.hasAgent("default"),
                "defaultRunEndpoint", "/agui/run",
                "pathRunEndpoint", "/agui/run/{agentId}",
                "sessionKey", "RunAgentInput.threadId",
                "protocol", "AG-UI");
    }
}
