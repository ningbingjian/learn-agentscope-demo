package com.example.agentscope.distributedstateandstorage;

import com.example.agentscope.distributedstateandstorage.store.DemoSharedAgentStateStore;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.remote.store.InMemoryStore;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SharedDistributedStoreTest {

    @Test
    void twoReplicasShareAgentStateAndUserScopedWorkspace() {
        AgentStateStore stateStore = new DemoSharedAgentStateStore();
        InMemoryStore baseStore = new InMemoryStore();
        DistributedStore shared = DistributedStore.builder()
                .agentStateStore(stateStore)
                .baseStore(baseStore)
                .build();

        try (HarnessAgent replicaA = buildReplica("target/replica-a", shared);
             HarnessAgent replicaB = buildReplica("target/replica-b", shared)) {

            RuntimeContext sessionA = RuntimeContext.builder()
                    .userId("alice")
                    .sessionId("shared-session")
                    .build();

            replicaA.call(new UserMessage("message-from-replica-a"), sessionA).block();

            AgentState restoredFromB = replicaB.getDelegate()
                    .getAgentState("alice", "shared-session");
            assertThat(restoredFromB).isNotNull();
            assertThat(restoredFromB.getContext())
                    .extracting(Msg::getTextContent)
                    .anyMatch(text -> text != null && text.contains("message-from-replica-a"));

            WorkspaceManager workspaceA = replicaA.workspaceFor("alice", "session-a");
            WorkspaceManager workspaceB = replicaB.workspaceFor("alice", "session-b");
            RuntimeContext ctxA = RuntimeContext.builder()
                    .userId("alice").sessionId("session-a").build();
            RuntimeContext ctxB = RuntimeContext.builder()
                    .userId("alice").sessionId("session-b").build();

            workspaceA.appendUtf8WorkspaceRelative(ctxA, "memory/shared-note.md", "from-a\n");
            String fromB = workspaceB.readManagedWorkspaceFileUtf8(ctxB, "memory/shared-note.md");

            assertThat(fromB).contains("from-a");
            assertThat(baseStore.size()).isPositive();
        }
    }

    private static HarnessAgent buildReplica(String workspace, DistributedStore store) {
        return HarnessAgent.builder()
                .name("distributed-agent")
                .model(new EchoModel())
                .workspace(Paths.get(workspace))
                .distributedStore(store)
                .filesystem(new RemoteFilesystemSpec()
                        .isolationScope(IsolationScope.USER))
                .disableMemoryHooks()
                .disableMemoryTools()
                .disableCompaction()
                .disableSubagents()
                .build();
    }

    private static final class EchoModel implements Model {
        @Override
        public Flux<ChatResponse> stream(
                List<Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            String last = messages.isEmpty() ? "empty" : messages.get(messages.size() - 1).getTextContent();
            ContentBlock content = TextBlock.builder().text("echo:" + last).build();
            return Flux.just(ChatResponse.builder()
                    .content(List.of(content))
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "echo-model";
        }
    }
}
