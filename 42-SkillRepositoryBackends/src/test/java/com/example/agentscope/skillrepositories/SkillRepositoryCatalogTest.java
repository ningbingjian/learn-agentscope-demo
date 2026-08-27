package com.example.agentscope.skillrepositories;

import com.example.agentscope.skillrepositories.service.SkillRepositoryCatalogService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillRepositoryCatalogTest {

    @Test
    void catalogContainsGitMysqlPostgresqlAndNacos() {
        SkillRepositoryCatalogService service = new SkillRepositoryCatalogService();
        assertThat(service.backends())
                .extracting(row -> row.get("name"))
                .containsExactly("git", "mysql", "postgresql", "nacos");
    }
}
