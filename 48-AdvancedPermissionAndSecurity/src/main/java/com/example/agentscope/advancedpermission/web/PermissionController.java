package com.example.agentscope.advancedpermission.web;

import com.example.agentscope.advancedpermission.service.PermissionLabService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permission")
public class PermissionController {
    private final PermissionLabService service;
    public PermissionController(PermissionLabService service) { this.service = service; }

    @GetMapping("/modes") public List<String> modes() { return service.modes(); }
    @GetMapping("/matrix") public Map<String, String> matrix() { return service.matrix(); }
    @GetMapping("/precedence") public Map<String, String> precedence() { return service.rulePrecedence(); }
    @GetMapping("/dynamic-rule") public Map<String, String> dynamicRule() { return service.dynamicRule(); }
}
