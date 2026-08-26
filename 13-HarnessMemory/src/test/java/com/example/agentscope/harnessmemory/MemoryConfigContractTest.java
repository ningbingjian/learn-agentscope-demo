package com.example.agentscope.harnessmemory;

import io.agentscope.harness.agent.memory.MemoryConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryConfigContractTest {

    @Test
    void supportsAlwaysNeverAndThrottledFlushPolicies() {
        assertThat(MemoryConfig.FlushTrigger.always().mode())
                .isEqualTo(MemoryConfig.FlushMode.ALWAYS);
        assertThat(MemoryConfig.FlushTrigger.never().mode())
                .isEqualTo(MemoryConfig.FlushMode.NEVER);

        MemoryConfig.FlushTrigger throttled =
                MemoryConfig.FlushTrigger.throttled(Duration.ofMinutes(10));
        assertThat(throttled.mode()).isEqualTo(MemoryConfig.FlushMode.THROTTLED);
        assertThat(throttled.minGap()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void validatesConsolidationPromptPlaceholdersEarly() {
        assertThatThrownBy(() -> MemoryConfig.builder()
                .consolidationPrompt("only one placeholder: %d")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly two %d placeholders");
    }

    @Test
    void keepsRetentionAndConsolidationSettingsInConfig() {
        MemoryConfig config = MemoryConfig.builder()
                .flushTrigger(MemoryConfig.FlushTrigger.throttled(Duration.ofMinutes(5)))
                .consolidationMinGap(Duration.ofHours(2))
                .consolidationMaxTokens(6000)
                .dailyFileRetentionDays(30)
                .sessionRetentionDays(60)
                .build();

        assertThat(config.consolidationMinGap()).isEqualTo(Duration.ofHours(2));
        assertThat(config.consolidationMaxTokens()).isEqualTo(6000);
        assertThat(config.dailyFileRetentionDays()).isEqualTo(30);
        assertThat(config.sessionRetentionDays()).isEqualTo(60);
    }
}
