package com.example.agentscope.skillselflearning.config;

import com.example.agentscope.skillselflearning.model.SkillLessonModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.skill.curator.SkillCuratorConfig;
import io.agentscope.harness.agent.skill.curator.SkillPromotionGate;
import io.agentscope.harness.agent.skill.curator.SkillVisibilityFilter;
import io.agentscope.harness.agent.tool.SkillManageConfig;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class AgentConfiguration {

    @Bean
    HarnessAgent skillAgent(@Value("${lesson.workspace:.agentscope/workspace}") String workspace) {
        SkillPromotionGate approvalGate = (candidate, ctx) -> Mono.just(
                new SkillPromotionGate.PromotionDecision.Approve(
                        "lesson-reviewer",
                        List.of("lesson"),
                        Instant.now()));
        SkillVisibilityFilter visibilityFilter =
                (List<AgentSkill> all, io.agentscope.core.agent.RuntimeContext ctx) -> List.copyOf(all);

        return HarnessAgent.builder()
                .name("skill-self-learning-agent")
                .sysPrompt("你是 Skill 治理学习助手。")
                .model(new SkillLessonModel())
                .workspace(Path.of(workspace))
                .disableMemoryHooks()
                .disableMemoryTools()
                .disableCompaction()
                .enableSkillManageTool(SkillManageConfig.defaults())
                .enableSkillPromotionGate(approvalGate, visibilityFilter)
                .environment("lesson")
                .enableSkillCurator(SkillCuratorConfig.builder()
                        .intervalHours(24)
                        .minIdleHours(0)
                        .staleAfterDays(30)
                        .archiveAfterDays(90)
                        .umbrellaPassMode(SkillCuratorConfig.UmbrellaPassMode.DISABLED)
                        .build())
                .build();
    }
}
