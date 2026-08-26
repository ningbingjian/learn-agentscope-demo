package com.example.agentscope.kubernetes;

import com.example.agentscope.kubernetes.health.AgentScopeReadinessHealthIndicator;
import io.agentscope.core.shutdown.GracefulShutdownConfig;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.shutdown.PartialReasoningPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentScopeReadinessHealthIndicatorTest {

    private final GracefulShutdownManager manager = GracefulShutdownManager.getInstance();

    @BeforeEach
    void setUp() {
        manager.resetForTesting();
        manager.setConfig(new GracefulShutdownConfig(
                Duration.ofSeconds(2),
                PartialReasoningPolicy.SAVE
        ));
    }

    @AfterEach
    void tearDown() {
        manager.resetForTesting();
    }

    @Test
    void readinessTurnsDownAfterDrain() {
        AgentScopeReadinessHealthIndicator indicator =
                new AgentScopeReadinessHealthIndicator(manager);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);

        manager.performGracefulShutdown();

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
