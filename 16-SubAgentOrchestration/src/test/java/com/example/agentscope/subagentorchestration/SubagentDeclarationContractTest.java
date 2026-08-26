package com.example.agentscope.subagentorchestration;

import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubagentDeclarationContractTest {

    @Test
    void capturesIsolationStepsPersistenceAndToolAllowlist() {
        SubagentDeclaration declaration = SubagentDeclaration.builder()
                .name("researcher")
                .description("技术调研专家")
                .inlineAgentsBody("只负责调研并返回结构化结论")
                .workspaceMode(WorkspaceMode.ISOLATED)
                .steps(6)
                .persistSession(true)
                .tools(List.of("read_file", "grep_files"))
                .build();

        assertThat(declaration.getName()).isEqualTo("researcher");
        assertThat(declaration.getWorkspaceMode()).isEqualTo(WorkspaceMode.ISOLATED);
        assertThat(declaration.getSteps()).isEqualTo(6);
        assertThat(declaration.isPersistSession()).isTrue();
        assertThat(declaration.getTools()).containsExactly("read_file", "grep_files");
        assertThat(declaration.getInlineAgentsBody()).contains("结构化结论");
    }
}
