package com.example.agentscope.skills;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.SkillUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillFormatTest {

    @Test
    void parsesSkillMarkdownFrontMatterAndInstructions() {
        String markdown = """
                ---
                name: code-reviewer
                description: Review Java code with a fixed checklist.
                ---

                # Code Reviewer

                1. Read the checklist.
                2. Report risks by severity.
                """;

        AgentSkill skill = SkillUtil.createFrom(markdown, null, "test");

        assertThat(skill.getName()).isEqualTo("code-reviewer");
        assertThat(skill.getDescription()).contains("fixed checklist");
        assertThat(skill.getSkillContent()).contains("Report risks by severity");
        assertThat(skill.getSkillId()).isEqualTo("code-reviewer_test");
    }

    @Test
    void rejectsSkillWithoutRequiredFrontMatter() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                SkillUtil.createFrom("# Missing metadata", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
