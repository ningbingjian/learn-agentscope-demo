package com.example.agentscope.asynctool;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.asynctool.bus.LessonAsyncToolRegistry;
import com.example.agentscope.asynctool.bus.LessonMessageBus;
import com.example.agentscope.asynctool.tool.SlowReportTools;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.bus.AsyncToolRecord;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AsyncToolContractTest {
    @Autowired HarnessAgent agent;
    @Autowired LessonMessageBus bus;
    @Autowired LessonAsyncToolRegistry registry;
    @Autowired SlowReportTools tools;

    @BeforeEach
    void reset() { bus.clear(); registry.clear(); tools.reset(); }

    @Test
    void slowToolIsOffloadedThenResultAndWakeupAreDelivered() throws Exception {
        RuntimeContext ctx = RuntimeContext.builder().userId("alice").sessionId("async-session").build();
        var reply = agent.call(new UserMessage("生成 AgentScope 日报"), ctx).block(Duration.ofSeconds(3));

        assertThat(reply).isNotNull();
        assertThat(reply.getTextContent()).contains("转入后台");

        Thread.sleep(450L);
        assertThat(tools.executions()).isEqualTo(1);
        assertThat(registry.snapshot()).hasSize(1);
        assertThat(registry.snapshot().get(0).record().status()).isEqualTo(AsyncToolRecord.COMPLETED);
        assertThat(registry.snapshot().get(0).result()).contains("report-ready");

        assertThat(bus.inboxHasMessages("async-session").block()).isTrue();
        var inbox = bus.inboxDrain("async-session", 10).block();
        assertThat(inbox).isNotEmpty();
        assertThat(inbox.get(0).payload().toString()).contains("running in background has completed");

        var wakeups = bus.queueDrain("agentscope:wakeups", 10).block();
        assertThat(wakeups).isNotEmpty();
        assertThat(wakeups.get(0).payload()).containsEntry("sessionId", "async-session");
    }
}
