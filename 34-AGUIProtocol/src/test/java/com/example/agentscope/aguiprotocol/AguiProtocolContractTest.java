package com.example.agentscope.aguiprotocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.aguiprotocol.model.AguiDemoModel;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.event.AguiEventType;
import io.agentscope.core.agui.model.AguiMessage;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.spring.boot.agui.mvc.AguiMvcController;
import io.agentscope.spring.boot.agui.mvc.AguiRestController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = AguiProtocolApplication.class)
class AguiProtocolContractTest {

    @Autowired AguiAgentRegistry registry;
    @Autowired AguiMvcController mvcController;
    @Autowired AguiRestController restController;

    @Test
    void springStarterActivatesWhenRegistryBeanExists() {
        assertThat(registry.hasAgent("assistant")).isTrue();
        assertThat(registry.hasAgent("default")).isTrue();
        assertThat(mvcController).isNotNull();
        assertThat(restController).isNotNull();
    }

    @Test
    void adapterMapsAgentEventsToAguiLifecycle() {
        ReActAgent agent = ReActAgent.builder()
                .name("contract-agent")
                .sysPrompt("test")
                .model(new AguiDemoModel())
                .build();
        AguiAgentAdapter adapter = new AguiAgentAdapter(
                agent,
                AguiAdapterConfig.builder()
                        .enableReasoning(true)
                        .build());
        RunAgentInput input = RunAgentInput.builder()
                .threadId("thread-1")
                .runId("run-1")
                .messages(List.of(AguiMessage.userMessage("msg-1", "hello")))
                .build();

        List<AguiEvent> events = adapter.run(input).collectList().block();

        assertThat(events).isNotNull().isNotEmpty();
        List<AguiEventType> types = events.stream().map(AguiEvent::getType).toList();
        assertThat(types).contains(
                AguiEventType.RUN_STARTED,
                AguiEventType.TEXT_MESSAGE_START,
                AguiEventType.TEXT_MESSAGE_CONTENT,
                AguiEventType.TEXT_MESSAGE_END,
                AguiEventType.RUN_FINISHED);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getThreadId()).isEqualTo("thread-1");
            assertThat(event.getRunId()).isEqualTo("run-1");
        });
    }
}
