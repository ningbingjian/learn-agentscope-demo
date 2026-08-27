package com.example.agentscope.memoryintegrations.web;

import com.example.agentscope.memoryintegrations.service.MemoryIntegrationCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryIntegrationController {

    private final MemoryIntegrationCatalogService service;

    public MemoryIntegrationController(MemoryIntegrationCatalogService service) {
        this.service = service;
    }

    @GetMapping("/providers")
    public List<Map<String, Object>> providers() {
        return service.providers();
    }

    @GetMapping("/contract")
    public Map<String, Object> contract() {
        return service.coreContract();
    }

    @GetMapping("/build-samples")
    public List<String> buildSamples() {
        return service.buildSamplesWithoutNetworkCalls();
    }
}
