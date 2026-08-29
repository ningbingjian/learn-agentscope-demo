package com.example.agentscope.consistency;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.consistency.service.ConsistencyService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ConsistencyContractTest {
    @Autowired ConsistencyService service;

    @Test
    void sameSessionIsSerializedWhileDifferentSessionsOverlap() {
        Map<String, Object> result = service.serializationDemo();
        assertThat(result.get("sameSessionMaxConcurrentModelCalls")).isEqualTo(1);
        assertThat((Integer) result.get("differentSessionMaxConcurrentModelCalls")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void staleWritersCompeteWithAtomicCasAndOnlyOneWins() {
        Map<String, Object> result = service.casRaceDemo();
        assertThat(result.get("created")).isEqualTo(true);
        assertThat(result.get("successfulWriters")).isEqualTo(1);
        assertThat((Long) result.get("latestVersion")).isGreaterThan((Long) result.get("expectedVersion"));
    }

    @Test
    void duplicateWakeupOrWebhookCanBeClaimedOnlyOnce() {
        Map<String, Object> result = service.idempotencyDemo();
        assertThat(result)
                .containsEntry("firstAccepted", true)
                .containsEntry("duplicateAccepted", false)
                .containsEntry("processedExactlyOnce", true);
    }
}
