package com.example.agentscope.evaluation.web;

import com.example.agentscope.evaluation.service.EvaluationService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eval")
public class EvaluationController {
    private final EvaluationService service;

    public EvaluationController(EvaluationService service) { this.service = service; }

    @GetMapping("/dataset") Object dataset() { return service.dataset(); }
    @GetMapping("/run") Object run() { return service.runAll(); }
    @GetMapping("/philosophy") Map<String, Object> philosophy() { return service.philosophy(); }
}
