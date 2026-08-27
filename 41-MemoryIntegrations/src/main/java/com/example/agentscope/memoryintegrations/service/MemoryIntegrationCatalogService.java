package com.example.agentscope.memoryintegrations.service;

import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.bailian.BailianLongTermMemory;
import io.agentscope.core.memory.mem0.Mem0ApiType;
import io.agentscope.core.memory.mem0.Mem0LongTermMemory;
import io.agentscope.core.memory.reme.ReMeLongTermMemory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@SuppressWarnings("removal")
public class MemoryIntegrationCatalogService {

    public List<Map<String, Object>> providers() {
        return List.of(
                provider("mem0", "agentscope-extensions-mem0", Mem0LongTermMemory.class,
                        "agentName/userId/runName + metadata filter", "Platform or self-hosted"),
                provider("reme", "agentscope-extensions-reme", ReMeLongTermMemory.class,
                        "userId -> workspace_id", "Self-hosted trajectory memory"),
                provider("bailian", "agentscope-extensions-memory-bailian", BailianLongTermMemory.class,
                        "userId + memoryLibraryId + projectId", "Alibaba Cloud managed memory")
        );
    }

    public Map<String, Object> coreContract() {
        Deprecated annotation = LongTermMemory.class.getAnnotation(Deprecated.class);
        return Map.of(
                "interface", LongTermMemory.class.getName(),
                "deprecated", annotation != null,
                "forRemoval", annotation != null && annotation.forRemoval(),
                "since", annotation == null ? "" : annotation.since(),
                "currentRecommendation", "Harness Memory or application-layer cross-session persistence"
        );
    }

    public List<String> buildSamplesWithoutNetworkCalls() {
        LongTermMemory mem0 = Mem0LongTermMemory.builder()
                .agentName("lesson-agent")
                .userId("alice")
                .apiBaseUrl("http://127.0.0.1:18090")
                .apiType(Mem0ApiType.SELF_HOSTED)
                .build();

        LongTermMemory reme = ReMeLongTermMemory.builder()
                .userId("alice-workspace")
                .apiBaseUrl("http://127.0.0.1:18091")
                .build();

        try (BailianLongTermMemory bailian = BailianLongTermMemory.builder()
                .apiKey("lesson-placeholder-key")
                .userId("alice")
                .memoryLibraryId("lesson-library")
                .projectId("lesson-project")
                .build()) {
            return List.of(
                    mem0.getClass().getSimpleName(),
                    reme.getClass().getSimpleName(),
                    bailian.getClass().getSimpleName()
            );
        }
    }

    private Map<String, Object> provider(
            String name, String artifact, Class<?> type, String isolation, String deployment) {
        return Map.of(
                "name", name,
                "artifact", artifact,
                "class", type.getName(),
                "implementsLegacyLongTermMemory", LongTermMemory.class.isAssignableFrom(type),
                "isolation", isolation,
                "deployment", deployment
        );
    }
}
