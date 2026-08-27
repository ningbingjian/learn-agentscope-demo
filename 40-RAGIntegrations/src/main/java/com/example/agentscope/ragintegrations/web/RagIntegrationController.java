package com.example.agentscope.ragintegrations.web;

import com.example.agentscope.ragintegrations.service.RagIntegrationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagIntegrationController {

    private final RagIntegrationService service;

    public RagIntegrationController(RagIntegrationService service) {
        this.service = service;
    }

    @GetMapping("/providers")
    public List<Map<String, String>> providers() {
        return service.providers();
    }

    @GetMapping("/retrieve")
    public List<Map<String, Object>> retrieve(@RequestParam String q) {
        return service.retrieve(q);
    }
}
