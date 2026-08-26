package com.example.agentscope.agentserviceanddeployment;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.extensions.agentprotocol.AgentProtocolAutoConfiguration;
import io.agentscope.extensions.agentprotocol.AgentProtocolController;
import io.agentscope.extensions.agentprotocol.AgentProtocolTaskEventBus;
import io.agentscope.extensions.agentprotocol.AgentProtocolTaskStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentProtocolAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentProtocolAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void registersProtocolBeansWhenEnabled() {
        runner.withPropertyValues("agentscope.agent-protocol.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentProtocolTaskEventBus.class);
                    assertThat(context).hasSingleBean(AgentProtocolTaskStore.class);
                    assertThat(context).hasSingleBean(AgentProtocolController.class);
                });
    }

    @Test
    void doesNotRegisterProtocolBeansWhenDisabled() {
        runner.withPropertyValues("agentscope.agent-protocol.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AgentProtocolTaskStore.class);
                    assertThat(context).doesNotHaveBean(AgentProtocolController.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        Model model() {
            return new NoopModel();
        }

        @Bean(destroyMethod = "close")
        HarnessAgent agent(Model model) {
            return HarnessAgent.builder()
                    .name("protocol-test-agent")
                    .model(model)
                    .workspace(Paths.get("target/protocol-test-workspace"))
                    .checkRunning(false)
                    .build();
        }

        @Bean
        WorkspaceManager workspaceManager(HarnessAgent agent) {
            return agent.getWorkspaceManager();
        }
    }

    private static final class NoopModel implements Model {
        @Override
        public Flux<ChatResponse> stream(
                List<io.agentscope.core.message.Msg> messages,
                List<ToolSchema> tools,
                GenerateOptions options
        ) {
            ContentBlock content = TextBlock.builder().text("ok").build();
            return Flux.just(ChatResponse.builder()
                    .content(List.of(content))
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "noop-model";
        }
    }
}
