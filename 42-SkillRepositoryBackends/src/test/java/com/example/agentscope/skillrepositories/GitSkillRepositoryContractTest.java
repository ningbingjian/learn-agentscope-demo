package com.example.agentscope.skillrepositories;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.GitSkillRepository;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitSkillRepositoryContractTest {

    @TempDir
    Path tempDir;

    @Test
    void localGitRepositoryCanBeClonedSyncedAndReadAsSkillRepository() throws Exception {
        Path remote = tempDir.resolve("remote-skills");
        Files.createDirectories(remote.resolve("skills/demo"));
        Files.writeString(remote.resolve("skills/demo/SKILL.md"), """
                ---
                name: demo-skill
                description: A local Git-backed lesson skill.
                ---

                # Demo Skill

                When asked to review a change, explain the risk first.
                """);

        try (Git git = Git.init().setDirectory(remote.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("add demo skill")
                    .setAuthor("lesson", "lesson@example.com")
                    .setCommitter("lesson", "lesson@example.com")
                    .call();
        }

        Path clone = tempDir.resolve("clone");
        try (GitSkillRepository repo = new GitSkillRepository(
                remote.toUri().toString(), null, clone, "lesson-git", false, "skills")) {
            repo.sync();

            assertThat(repo.isWriteable()).isFalse();
            assertThat(repo.skillExists("demo")).isTrue();
            assertThat(repo.getAllSkillNames()).containsExactly("demo");

            AgentSkill skill = repo.getSkill("demo");
            assertThat(skill.getName()).isEqualTo("demo-skill");
            assertThat(skill.getDescription()).contains("Git-backed");
            assertThat(repo.getSource()).isEqualTo("lesson-git");
        }
    }
}
