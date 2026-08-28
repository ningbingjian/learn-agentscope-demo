package com.example.agentscope.runtimeextension.web;

import com.example.agentscope.runtimeextension.service.RuntimeExtensionService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime-extension")
public class RuntimeExtensionController {
    private final RuntimeExtensionService service;
    public RuntimeExtensionController(RuntimeExtensionService service) { this.service = service; }

    @GetMapping("/contract") public Map<String, Object> contract() { return service.contract(); }
    @PostMapping("/call") public Map<String, Object> call(@RequestBody CallRequest request) { return service.call(request.message()); }
    public record CallRequest(String message) {}
}
