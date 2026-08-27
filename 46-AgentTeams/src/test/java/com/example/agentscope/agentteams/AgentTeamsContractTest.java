package com.example.agentscope.agentteams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.agentscope.agentteams.service.TeamBoardService;
import com.example.agentscope.agentteams.service.TeamBoardService.TeamConflictException;
import org.junit.jupiter.api.Test;

class AgentTeamsContractTest {

    @Test
    void officialAgentTeamsClassesAreNotPartOfLocked201Classpath() {
        assertThatThrownBy(() -> Class.forName("io.agentscope.harness.agent.middleware.TeamsMiddleware"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("io.agentscope.harness.agent.tool.TeamTool"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void applicationTeamBoardUsesOptimisticVersioningAndMailbox() {
        TeamBoardService service = new TeamBoardService();
        var created = service.createTask("review AgentScope permission design");
        assertThat(created.version()).isZero();

        var claimed = service.claim(created.id(), "worker-a", 0L);
        assertThat(claimed.owner()).isEqualTo("worker-a");
        assertThat(claimed.version()).isEqualTo(1L);

        assertThatThrownBy(() -> service.claim(created.id(), "worker-b", 0L))
                .isInstanceOf(TeamConflictException.class)
                .hasMessageContaining("stale task version");

        var completed = service.complete(created.id(), "worker-a", 1L, "done");
        assertThat(completed.version()).isEqualTo(2L);
        assertThat(completed.result()).isEqualTo("done");

        service.sendMessage("worker-a", "lead", "task finished");
        assertThat(service.messagesFor("lead")).hasSize(1);
    }
}
