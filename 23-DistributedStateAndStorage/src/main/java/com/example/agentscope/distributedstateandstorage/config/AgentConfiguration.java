package com.example.agentscope.distributedstateandstorage.config;

import com.example.agentscope.distributedstateandstorage.store.DemoSharedAgentStateStore;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.remote.store.InMemoryStore;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    AgentStateStore sharedStateStore() {
        return new DemoSharedAgentStateStore();
    }

    @Bean
    InMemoryStore sharedBaseStore() {
        return new InMemoryStore();
    }

    @Bean
    DistributedStore distributedStore(
            AgentStateStore sharedStateStore,
            InMemoryStore sharedBaseStore
    ) {
        return DistributedStore.builder()
                .agentStateStore(sharedStateStore)
                .baseStore(sharedBaseStore)
                .build();
    }

    @Bean(destroyMethod = "close")
    HarnessAgent distributedAgent(Model model, DistributedStore distributedStore) {
        return HarnessAgent.builder()
                .name("distributed-agent")
                .sysPrompt("你是一个运行在多副本架构中的 Agent。会话状态与共享工作区由 DistributedStore 管理。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .distributedStore(distributedStore)
                .filesystem(new RemoteFilesystemSpec()
                        .isolationScope(IsolationScope.USER))
                .build();
    }
}
