package com.example.agentscope.chatcompat.web;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-compat")
public class CompatibilityInfoController {

    private final AtomicInteger agentCreationCounter;

    public CompatibilityInfoController(AtomicInteger agentCreationCounter) {
        this.agentCreationCounter = agentCreationCounter;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "protocol", "OpenAI Chat Completions compatible",
                "endpoint", "/v1/chat/completions",
                "stateless", true,
                "historyOwner", "client",
                "agentScope", "prototype per request",
                "agentsCreated", agentCreationCounter.get());
    }
}
