package com.example.agentscope.agentserviceanddeployment.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/service")
public class ServiceInfoController {

    @GetMapping("/info")
    Map<String, Object> info() {
        return Map.of(
                "service", "22-AgentServiceAndDeployment",
                "protocol", "Agent Protocol",
                "taskEndpoints", List.of(
                        "POST /tasks",
                        "GET /tasks/{taskId}",
                        "GET /tasks/{taskId}/wait",
                        "POST /tasks/{taskId}/cancel",
                        "GET /tasks/{taskId}/events",
                        "POST /tasks/{taskId}/resume"
                )
        );
    }
}
