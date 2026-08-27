package com.example.agentscope.studiotraining;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.studiotraining.service.StudioTrainingCatalogService;
import io.agentscope.core.training.runner.TrainingRunner;
import io.agentscope.core.training.strategy.SamplingRateStrategy;
import org.junit.jupiter.api.Test;

class StudioAndTrainingContractTest {

    @Test
    void studioAndTrainingTypesAreOnClasspath() {
        StudioTrainingCatalogService service = new StudioTrainingCatalogService();

        assertThat(service.components())
                .extracting(item -> item.get("name"))
                .contains(
                        "studio-manager",
                        "studio-message-hook",
                        "studio-user-agent",
                        "training-runner",
                        "sampling-strategy",
                        "explicit-marking");
    }

    @Test
    void samplingRateStrategyKeepsConfiguredProbability() {
        SamplingRateStrategy strategy = SamplingRateStrategy.of(0.25);

        assertThat(strategy.getSampleRate()).isEqualTo(0.25);
    }

    @Test
    void trainingRunnerCanBeBuiltWithoutStartingNetworkPipeline() {
        TrainingRunner runner = TrainingRunner.builder()
                .trinityEndpoint("http://127.0.0.1:18090")
                .modelName("lesson-training-model")
                .selectionStrategy(SamplingRateStrategy.of(0.10))
                .rewardCalculator(agent -> 0.0)
                .commitIntervalSeconds(0)
                .build();

        assertThat(runner.isRunning()).isFalse();
        assertThat(runner.getConfig().getTrinityEndpoint()).isEqualTo("http://127.0.0.1:18090");
        assertThat(runner.getConfig().getModelName()).isEqualTo("lesson-training-model");
        assertThat(runner.getConfig().getSampleRate()).isEqualTo(0.10);
        assertThat(runner.getConfig().getCommitIntervalSeconds()).isZero();
    }

    @Test
    void webPreviewDoesNotStartRunner() {
        StudioTrainingCatalogService service = new StudioTrainingCatalogService();

        assertThat(service.trainingPreview())
                .containsEntry("running", false)
                .containsEntry("modelName", "lesson-training-model")
                .containsEntry("sampleRate", 0.10);
    }
}
