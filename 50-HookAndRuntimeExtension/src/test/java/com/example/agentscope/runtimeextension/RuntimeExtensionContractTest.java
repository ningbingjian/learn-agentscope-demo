package com.example.agentscope.runtimeextension;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.runtimeextension.hook.LegacyCountingHook;
import com.example.agentscope.runtimeextension.middleware.CountingMiddleware;
import com.example.agentscope.runtimeextension.model.HookDemoModel;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.message.UserMessage;
import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
class RuntimeExtensionContractTest {

    @Test
    void middlewareIsThePreferredTwoDotZeroExtensionPath() {
        HookDemoModel model = new HookDemoModel();
        CountingMiddleware middleware = new CountingMiddleware();
        try (ReActAgent agent = ReActAgent.builder()
                .name("middleware-test")
                .sysPrompt("base")
                .model(model)
                .middleware(middleware)
                .build()) {
            agent.call(new UserMessage("hello")).block();
            assertThat(middleware.snapshot().get("agent")).isEqualTo(1);
            assertThat(middleware.snapshot().get("reasoning")).isGreaterThanOrEqualTo(1);
            assertThat(middleware.snapshot().get("modelCall")).isGreaterThanOrEqualTo(1);
            assertThat(middleware.snapshot().get("systemPrompt")).isGreaterThanOrEqualTo(1);
            assertThat(model.lastSystemPrompt()).contains("[middleware-marker]");
        }
    }

    @Test
    void legacyHookStillRunsButIsDeprecatedForRemoval() {
        Deprecated deprecated = Hook.class.getAnnotation(Deprecated.class);
        assertThat(deprecated).isNotNull();
        assertThat(deprecated.forRemoval()).isTrue();
        assertThat(deprecated.since()).isEqualTo("2.0.0");

        LegacyCountingHook hook = new LegacyCountingHook(20);
        try (ReActAgent agent = ReActAgent.builder()
                .name("legacy-hook-test")
                .sysPrompt("base")
                .model(new HookDemoModel())
                .hook(hook)
                .build()) {
            agent.call(new UserMessage("hello")).block();
            assertThat(hook.count()).isGreaterThan(0);
            assertThat(hook.eventTypes()).isNotEmpty();
        }
    }

    @Test
    void systemHookIsCopiedIntoAgentsCreatedWhileItIsRegistered() {
        LegacyCountingHook systemHook = new LegacyCountingHook(5);
        AgentBase.addSystemHook(systemHook);
        ReActAgent included = null;
        ReActAgent excluded = null;
        try {
            included = ReActAgent.builder()
                    .name("included")
                    .sysPrompt("base")
                    .model(new HookDemoModel())
                    .build();
            AgentBase.removeSystemHook(systemHook);
            excluded = ReActAgent.builder()
                    .name("excluded")
                    .sysPrompt("base")
                    .model(new HookDemoModel())
                    .build();

            included.call(new UserMessage("first")).block();
            int afterIncluded = systemHook.count();
            assertThat(afterIncluded).isGreaterThan(0);

            excluded.call(new UserMessage("second")).block();
            assertThat(systemHook.count()).isEqualTo(afterIncluded);
        } finally {
            AgentBase.removeSystemHook(systemHook);
            if (included != null) included.close();
            if (excluded != null) excluded.close();
        }
    }
}
