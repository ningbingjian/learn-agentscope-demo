package com.example.agentscope.skills.service;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.SkillUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class SkillCatalogService {

    private final Path skillsRoot = Paths.get(".agentscope/workspace/skills");

    public List<SkillSummary> listSkills() {
        if (!Files.isDirectory(skillsRoot)) {
            return List.of();
        }

        List<SkillSummary> result = new ArrayList<>();
        try (Stream<Path> directories = Files.list(skillsRoot)) {
            directories
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(directory -> parse(directory, result));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list workspace skills", e);
        }
        return result;
    }

    private void parse(Path skillDirectory, List<SkillSummary> result) {
        Path skillFile = skillDirectory.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            return;
        }
        try {
            String markdown = Files.readString(skillFile);
            AgentSkill skill = SkillUtil.createFrom(markdown, null, "workspace-demo");
            result.add(new SkillSummary(
                    skill.getName(),
                    skill.getDescription(),
                    skill.getSkillId(),
                    skillsRoot.relativize(skillFile).toString().replace('\\', '/')
            ));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + skillFile, e);
        }
    }

    public record SkillSummary(
            String name,
            String description,
            String skillId,
            String path) {
    }
}
