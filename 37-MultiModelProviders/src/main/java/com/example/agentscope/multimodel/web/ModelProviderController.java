package com.example.agentscope.multimodel.web;

import com.example.agentscope.multimodel.service.ModelProviderCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelProviderController {

    private final ModelProviderCatalogService catalogService;

    public ModelProviderController(ModelProviderCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/providers")
    public List<ModelProviderCatalogService.ProviderView> providers() {
        return catalogService.providers();
    }

    @GetMapping("/can-resolve")
    public ModelProviderCatalogService.ResolveCheck canResolve(@RequestParam String modelId) {
        return catalogService.check(modelId);
    }
}
