package com.example.agentscope.skillrepositories.web;

import com.example.agentscope.skillrepositories.service.SkillRepositoryCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skill-repositories")
public class SkillRepositoryController {

    private final SkillRepositoryCatalogService service;

    public SkillRepositoryController(SkillRepositoryCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> backends() {
        return service.backends();
    }
}
