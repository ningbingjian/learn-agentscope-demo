package com.example.agentscope.modelruntime.web;

import com.example.agentscope.modelruntime.service.ModelRuntimeService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-runtime")
public class ModelRuntimeController {
    private final ModelRuntimeService service;

    public ModelRuntimeController(ModelRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/options")
    public Map<String, Object> options() { return service.configuredOptions(); }

    @GetMapping("/multimodal")
    public Map<String, Object> multimodal() { return service.multimodalSample(); }

    @GetMapping("/formatter")
    public Map<String, Object> formatter() { return service.formatterDemo(); }

    @PostMapping("/call")
    public Map<String, Object> call(@RequestBody CallRequest request) { return service.call(request.message()); }

    public record CallRequest(String message) {}
}
