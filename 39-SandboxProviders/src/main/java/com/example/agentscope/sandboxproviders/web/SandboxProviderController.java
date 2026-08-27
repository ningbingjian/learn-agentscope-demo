package com.example.agentscope.sandboxproviders.web;

import com.example.agentscope.sandboxproviders.service.SandboxProviderCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sandboxes")
public class SandboxProviderController {

    private final SandboxProviderCatalogService catalogService;

    public SandboxProviderController(SandboxProviderCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/providers")
    public List<SandboxProviderCatalogService.ProviderInfo> providers() {
        return catalogService.providers();
    }

    @GetMapping("/spec/{provider}")
    public SandboxProviderCatalogService.SpecView exampleSpec(@PathVariable String provider) {
        return catalogService.buildExampleSpec(provider);
    }
}
