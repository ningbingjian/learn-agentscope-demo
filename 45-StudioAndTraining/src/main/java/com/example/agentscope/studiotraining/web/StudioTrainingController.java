package com.example.agentscope.studiotraining.web;

import com.example.agentscope.studiotraining.service.StudioTrainingCatalogService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ecosystem")
public class StudioTrainingController {

    private final StudioTrainingCatalogService service;

    public StudioTrainingController(StudioTrainingCatalogService service) {
        this.service = service;
    }

    @GetMapping("/components")
    public List<Map<String, Object>> components() {
        return service.components();
    }

    @GetMapping("/training-preview")
    public Map<String, Object> trainingPreview() {
        return service.trainingPreview();
    }
}
