package com.example.agentscope.studiotraining.service;

import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import io.agentscope.core.studio.StudioUserAgent;
import io.agentscope.core.training.runner.TrainingRunner;
import io.agentscope.core.training.strategy.ExplicitMarkingStrategy;
import io.agentscope.core.training.strategy.SamplingRateStrategy;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StudioTrainingCatalogService {

    public List<Map<String, Object>> components() {
        return List.of(
                component("studio-manager", "agentscope-extensions-studio", StudioManager.class,
                        "Initialize Studio HTTP/WebSocket connection"),
                component("studio-message-hook", "agentscope-extensions-studio", StudioMessageHook.class,
                        "Push Agent messages and trace data to Studio"),
                component("studio-user-agent", "agentscope-extensions-studio", StudioUserAgent.class,
                        "Human-in-the-loop user represented by Studio UI"),
                component("training-runner", "agentscope-extensions-training", TrainingRunner.class,
                        "Sampling, trajectory, reward and Trinity commit pipeline"),
                component("sampling-strategy", "agentscope-extensions-training", SamplingRateStrategy.class,
                        "Random percentage-based training selection"),
                component("explicit-marking", "agentscope-extensions-training", ExplicitMarkingStrategy.class,
                        "Caller-controlled training selection")
        );
    }

    public Map<String, Object> trainingPreview() {
        TrainingRunner runner = TrainingRunner.builder()
                .trinityEndpoint("http://127.0.0.1:18090")
                .modelName("lesson-training-model")
                .selectionStrategy(SamplingRateStrategy.of(0.10))
                .rewardCalculator(agent -> 0.0)
                .commitIntervalSeconds(0)
                .build();

        return Map.of(
                "running", runner.isRunning(),
                "trinityEndpoint", runner.getConfig().getTrinityEndpoint(),
                "modelName", runner.getConfig().getModelName(),
                "sampleRate", runner.getConfig().getSampleRate(),
                "commitIntervalSeconds", runner.getConfig().getCommitIntervalSeconds()
        );
    }

    private Map<String, Object> component(
            String name, String artifact, Class<?> type, String role) {
        return Map.of(
                "name", name,
                "artifact", artifact,
                "class", type.getName(),
                "role", role
        );
    }
}
