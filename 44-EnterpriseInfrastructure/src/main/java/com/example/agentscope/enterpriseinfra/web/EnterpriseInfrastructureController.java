package com.example.agentscope.enterpriseinfra.web;

import com.example.agentscope.enterpriseinfra.service.EnterpriseInfrastructureService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/infrastructure")
public class EnterpriseInfrastructureController {

    private final EnterpriseInfrastructureService service;

    public EnterpriseInfrastructureController(EnterpriseInfrastructureService service) {
        this.service = service;
    }

    @GetMapping("/components")
    public List<Map<String, Object>> components() {
        return service.components();
    }

    @GetMapping("/schedules")
    public Map<String, Object> schedules() {
        return service.scheduleExamples();
    }
}
