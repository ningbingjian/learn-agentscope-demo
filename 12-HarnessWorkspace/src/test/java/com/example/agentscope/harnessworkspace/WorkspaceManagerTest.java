package com.example.agentscope.harnessworkspace;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void readsCoreWorkspaceFilesWithoutCallingModel() throws IOException {
        Files.writeString(tempDir.resolve("AGENTS.md"), "answer in Chinese");
        Files.writeString(tempDir.resolve("MEMORY.md"), "port=18081");
        Files.createDirectories(tempDir.resolve("knowledge"));
        Files.writeString(
                tempDir.resolve("knowledge/KNOWLEDGE.md"),
                "Workspace is not AgentState"
        );

        try (WorkspaceManager workspace = new WorkspaceManager(tempDir)) {
            RuntimeContext context = RuntimeContext.empty();

            assertThat(workspace.readAgentsMd(context)).contains("Chinese");
            assertThat(workspace.readMemoryMd(context)).contains("18081");
            assertThat(workspace.readKnowledgeMd(context)).contains("not AgentState");
            assertThat(workspace.listKnowledgeFiles(context)).hasSize(1);
        }
    }
}
