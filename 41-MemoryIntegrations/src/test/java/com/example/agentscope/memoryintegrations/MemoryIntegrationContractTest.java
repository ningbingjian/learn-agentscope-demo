package com.example.agentscope.memoryintegrations;

import com.example.agentscope.memoryintegrations.service.MemoryIntegrationCatalogService;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.mem0.Mem0ApiType;
import io.agentscope.core.memory.mem0.Mem0LongTermMemory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("removal")
class MemoryIntegrationContractTest {

    @Test
    void allThreeOfficialImplementationsCanBeConstructedWithoutCallingRemoteService() {
        MemoryIntegrationCatalogService service = new MemoryIntegrationCatalogService();
        assertThat(service.buildSamplesWithoutNetworkCalls())
                .containsExactly("Mem0LongTermMemory", "ReMeLongTermMemory", "BailianLongTermMemory");
    }

    @Test
    void coreLongTermMemoryContractIsExplicitlyDeprecatedForRemovalIn201() {
        Deprecated deprecated = LongTermMemory.class.getAnnotation(Deprecated.class);
        assertThat(deprecated).isNotNull();
        assertThat(deprecated.forRemoval()).isTrue();
        assertThat(deprecated.since()).isEqualTo("2.0.0");
    }

    @Test
    void mem0RequiresAtLeastOneIsolationIdentifier() {
        assertThatThrownBy(() -> Mem0LongTermMemory.builder()
                .apiBaseUrl("http://127.0.0.1:18090")
                .apiType(Mem0ApiType.SELF_HOSTED)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
