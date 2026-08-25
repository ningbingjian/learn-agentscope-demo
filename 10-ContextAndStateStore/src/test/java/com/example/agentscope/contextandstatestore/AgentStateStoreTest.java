package com.example.agentscope.contextandstatestore;

import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void inMemoryStoreKeepsStateInCurrentProcess() {
        InMemoryAgentStateStore store = new InMemoryAgentStateStore();
        AgentState state = sampleState();

        store.save("alice", "session-1", "agent_state", state);

        AgentState loaded = store
                .get("alice", "session-1", "agent_state", AgentState.class)
                .orElseThrow();
        assertThat(loaded.getSummary()).isEqualTo("demo summary");
        assertThat(loaded.getContext()).hasSize(1);
        assertThat(store.getSessionCount()).isEqualTo(1);
    }

    @Test
    void jsonFileStoreCanBeRecreatedAndReloadSameState() {
        Path stateRoot = tempDir.resolve("state");
        JsonFileAgentStateStore firstStore = new JsonFileAgentStateStore(stateRoot);
        firstStore.save("alice", "session-1", "agent_state", sampleState());

        JsonFileAgentStateStore secondStore = new JsonFileAgentStateStore(stateRoot);
        AgentState loaded = secondStore
                .get("alice", "session-1", "agent_state", AgentState.class)
                .orElseThrow();

        assertThat(loaded.getUserId()).isEqualTo("alice");
        assertThat(loaded.getSessionId()).isEqualTo("session-1");
        assertThat(loaded.getSummary()).isEqualTo("demo summary");
        assertThat(loaded.getContext()).hasSize(1);
    }

    private static AgentState sampleState() {
        return AgentState.builder()
                .userId("alice")
                .sessionId("session-1")
                .summary("demo summary")
                .addMessage(new UserMessage("记住：项目代号是 Orion"))
                .build();
    }
}
