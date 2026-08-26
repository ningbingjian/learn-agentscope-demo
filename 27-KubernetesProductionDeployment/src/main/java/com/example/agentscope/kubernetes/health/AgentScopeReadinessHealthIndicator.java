package com.example.agentscope.kubernetes.health;

import io.agentscope.core.shutdown.GracefulShutdownManager;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("agentScopeReadinessHealthIndicator")
public class AgentScopeReadinessHealthIndicator implements HealthIndicator {

    private final GracefulShutdownManager manager;

    public AgentScopeReadinessHealthIndicator(GracefulShutdownManager manager) {
        this.manager = manager;
    }

    @Override
    public Health health() {
        if (manager.isAcceptingRequests()) {
            return Health.up()
                    .withDetail("shutdownState", manager.getState().name())
                    .withDetail("activeRequests", manager.getActiveRequestCount())
                    .build();
        }
        return Health.down()
                .withDetail("shutdownState", manager.getState().name())
                .withDetail("activeRequests", manager.getActiveRequestCount())
                .build();
    }
}
