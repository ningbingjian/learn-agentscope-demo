package com.example.agentscope.contextbudget.web;

import com.example.agentscope.contextbudget.service.ContextBudgetService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/context-budget")
public class ContextBudgetController {
    private final ContextBudgetService service;
    public ContextBudgetController(ContextBudgetService service) { this.service = service; }

    @GetMapping("/plan")
    public Map<String, Object> plan() { return service.plannedBudget(); }

    @GetMapping("/compaction")
    public Map<String, Object> compaction(@RequestParam(defaultValue = "128000") int contextWindow) {
        return service.dynamicCompaction(contextWindow);
    }

    @GetMapping("/eviction")
    public Map<String, Object> eviction() { return service.evictionDefaults(); }
}
