package com.example.agentscope.security.web;

import com.example.agentscope.security.service.SecurityArchitectureService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security")
public class SecurityArchitectureController {
    private final SecurityArchitectureService service;

    public SecurityArchitectureController(SecurityArchitectureService service) { this.service = service; }

    @GetMapping("/architecture") Map<String, Object> architecture() { return service.architecture(); }
    @GetMapping("/tool-surface") Map<String, Object> toolSurface() { return service.toolSurfaceDemo(); }
    @GetMapping("/permission") Map<String, Object> permission(@RequestParam String path) { return service.permissionDemo(path); }

    @PostMapping("/skill-scan")
    Map<String, Object> skillScan(@RequestBody ScanRequest request) {
        return service.scanSkill(request.content(), request.trustLevel());
    }

    @PostMapping("/retrieved-content")
    Map<String, Object> retrieved(@RequestBody ContentRequest request) {
        return service.retrievedContentBoundary(request.content());
    }

    public record ScanRequest(String content, String trustLevel) {}
    public record ContentRequest(String content) {}
}
