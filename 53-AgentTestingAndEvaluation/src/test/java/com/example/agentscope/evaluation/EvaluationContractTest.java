package com.example.agentscope.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.evaluation.service.EvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EvaluationContractTest {
    @Autowired EvaluationService service;

    @Test
    void datasetRunsThroughRealAgentAndPassesBehaviorGate() {
        var report = service.runAll();
        assertThat(report.totalCases()).isEqualTo(3);
        assertThat(report.passedCases()).isEqualTo(3);
        assertThat(report.passRate()).isEqualTo(1.0);
        assertThat(report.toolSelectionAccuracy()).isEqualTo(1.0);
        assertThat(report.argumentAccuracy()).isEqualTo(1.0);
        assertThat(report.gatePassed()).isTrue();
        assertThat(report.totalTokens()).isGreaterThan(0);
        assertThat(report.estimatedCostUsd()).isGreaterThan(0.0);
    }

    @Test
    void datasetContainsToolAndNoToolScenarios() {
        assertThat(service.dataset()).extracting(EvaluationService.EvalCase::id)
                .containsExactly("weather-beijing", "weather-shanghai", "greeting");
    }
}
