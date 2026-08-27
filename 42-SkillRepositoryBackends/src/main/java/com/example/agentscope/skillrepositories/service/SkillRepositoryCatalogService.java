package com.example.agentscope.skillrepositories.service;

import io.agentscope.core.nacos.skill.NacosSkillRepository;
import io.agentscope.core.skill.repository.GitSkillRepository;
import io.agentscope.core.skill.repository.mysql.MysqlSkillRepository;
import io.agentscope.core.skill.repository.postgresql.PostgresSkillRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SkillRepositoryCatalogService {

    public List<Map<String, Object>> backends() {
        return List.of(
                backend("git", "agentscope-extensions-skill-git-repository", GitSkillRepository.class,
                        false, "Git review/versioning, distributed read replicas via local clone"),
                backend("mysql", "agentscope-extensions-skill-mysql-repository", MysqlSkillRepository.class,
                        true, "Online CRUD from management console, transactional DB storage"),
                backend("postgresql", "agentscope-extensions-skill-postgresql-repository", PostgresSkillRepository.class,
                        true, "PostgreSQL-backed CRUD skill center"),
                backend("nacos", "agentscope-extensions-nacos-skill", NacosSkillRepository.class,
                        true, "Centralized configuration/discovery style skill repository")
        );
    }

    private Map<String, Object> backend(
            String name, String artifact, Class<?> type, boolean writeable, String useCase) {
        return Map.of(
                "name", name,
                "artifact", artifact,
                "class", type.getName(),
                "writeable", writeable,
                "useCase", useCase
        );
    }
}
