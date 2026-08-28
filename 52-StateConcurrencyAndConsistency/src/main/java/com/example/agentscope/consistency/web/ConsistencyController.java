package com.example.agentscope.consistency.web;

import com.example.agentscope.consistency.service.ConsistencyService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consistency")
public class ConsistencyController {
    private final ConsistencyService service;

    public ConsistencyController(ConsistencyService service) {
        this.service = service;
    }

    @GetMapping("/serialization")
    Map<String, Object> serialization() { return service.serializationDemo(); }

    @GetMapping("/cas-race")
    Map<String, Object> casRace() { return service.casRaceDemo(); }

    @GetMapping("/idempotency")
    Map<String, Object> idempotency() { return service.idempotencyDemo(); }

    @GetMapping("/architecture")
    Map<String, Object> architecture() { return service.architecture(); }
}
