package com.example.agentscope.production.web;

import com.example.agentscope.production.middleware.ProductionTelemetryMiddleware;
import com.example.agentscope.production.service.ProductionArchitectureService;
import com.example.agentscope.production.service.ProductionChatService;
import com.example.agentscope.production.service.ProductionEvalService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production")
public class ProductionArchitectureController {
    private final ProductionChatService chatService;
    private final ProductionArchitectureService architectureService;
    private final ProductionEvalService evalService;
    private final ProductionTelemetryMiddleware telemetry;

    public ProductionArchitectureController(
            ProductionChatService chatService,
            ProductionArchitectureService architectureService,
            ProductionEvalService evalService,
            ProductionTelemetryMiddleware telemetry) {
        this.chatService = chatService;
        this.architectureService = architectureService;
        this.evalService = evalService;
        this.telemetry = telemetry;
    }

    @PostMapping("/chat")
    public ProductionChatService.ChatResult chat(@RequestBody ProductionChatService.ChatRequest request) {
        return chatService.chat(request);
    }

    @GetMapping("/architecture")
    public Map<String, Object> architecture() { return architectureService.architecture(); }

    @GetMapping("/decisions")
    public List<ProductionArchitectureService.Decision> decisions() { return architectureService.decisions(); }

    @GetMapping("/readiness")
    public Map<String, Object> readiness() { return architectureService.readiness(); }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() { return telemetry.snapshot(); }

    @PostMapping("/eval")
    public ProductionEvalService.GateReport eval() { return evalService.runGate(); }
}
