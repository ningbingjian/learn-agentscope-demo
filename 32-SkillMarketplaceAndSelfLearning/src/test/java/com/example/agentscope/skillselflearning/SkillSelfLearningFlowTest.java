package com.example.agentscope.skillselflearning;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.skillselflearning.model.SkillLessonModel;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.skill.curator.SkillPromotionGate;
import io.agentscope.harness.agent.skill.curator.SkillVisibilityFilter;
import io.agentscope.harness.agent.tool.SkillManageConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

class SkillSelfLearningFlowTest {

    @TempDir Path tempDir;

    @Test
    void proposeCreatesDraftAndPromotionMovesSkillToLiveDirectory() throws Exception {
        SkillPromotionGate gate = (candidate, ctx) -> Mono.just(
                new SkillPromotionGate.PromotionDecision.Approve(
                        "test-reviewer",
                        List.of("lesson"),
                        Instant.now()));
        SkillVisibilityFilter filter =
                (List<AgentSkill> all, RuntimeContext ctx) -> List.copyOf(all);
        HarnessAgent agent = HarnessAgent.builder()
                .name("test-skill-agent")
                .sysPrompt("test")
                .model(new SkillLessonModel())
                .workspace(tempDir)
                .disableMemoryHooks()
                .disableMemoryTools()
                .disableCompaction()
                .enableSkillManageTool(SkillManageConfig.defaults())
                .enableSkillPromotionGate(gate, filter)
                .environment("lesson")
                .build();

        assertThat(agent.getToolkit().getToolNames())
                .contains("propose_skill", "skill_manage");

        Map<String, Object> input = Map.of(
                "name", "lesson-note",
                "description", "记录可复用学习笔记",
                "body", "# Lesson Note\n\n1. 收集事实\n2. 输出摘要");
        ToolUseBlock use = ToolUseBlock.builder()
                .id("propose-1")
                .name("propose_skill")
                .input(input)
                .build();
        ToolResultBlock proposed = agent.getToolkit().callTool(ToolCallParam.builder()
                .toolUseBlock(use)
                .input(input)
                .agent(agent)
                .runtimeContext(RuntimeContext.empty())
                .build()).block();

        assertThat(proposed).isNotNull();
        String proposedText = proposed.getOutput().stream()
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::getText)
                .collect(Collectors.joining("\n"));
        assertThat(proposedText).contains("lesson-note");
        Path draft = tempDir.resolve("skills/_drafts/lesson-note/SKILL.md");
        assertThat(Files.exists(draft)).isTrue();

        Object promotion = agent.promoteSkill(
                "lesson-note",
                "test-reviewer",
                RuntimeContext.empty()).block();

        assertThat(promotion).isNotNull();
        Path live = tempDir.resolve("skills/lesson-note/SKILL.md");
        assertThat(Files.exists(live)).isTrue();
        assertThat(Files.readString(live)).contains("name: lesson-note");
        assertThat(Files.exists(draft)).isFalse();
    }
}
