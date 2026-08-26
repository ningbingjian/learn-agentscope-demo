package com.example.agentscope.modelregistry;

import io.agentscope.core.model.CachePolicy;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRegistryContractTest {

    @BeforeEach
    void resetBefore() {
        ModelRegistry.reset();
    }

    @AfterEach
    void resetAfter() {
        ModelRegistry.reset();
    }

    @Test
    void serviceLoaderDiscoversLessonModelProvider() {
        assertThat(ModelRegistry.canResolve("lesson:echo")).isTrue();

        Model first = ModelRegistry.resolve("lesson:echo");
        Model second = ModelRegistry.resolve("lesson:echo");

        assertThat(first.getModelName()).isEqualTo("lesson:echo");
        assertThat(second).isSameAs(first);
    }

    @Test
    void nonEmptyContextIsNotCachedByDefaultButCanOptInWithCacheId() {
        ModelCreationContext defaultPolicy = ModelCreationContext.builder()
                .baseUrl("https://tenant-a.example.invalid")
                .build();
        Model first = ModelRegistry.resolve("lesson:echo", defaultPolicy);
        Model second = ModelRegistry.resolve("lesson:echo", defaultPolicy);
        assertThat(second).isNotSameAs(first);

        ModelCreationContext cached = ModelCreationContext.builder()
                .baseUrl("https://tenant-a.example.invalid")
                .cachePolicy(CachePolicy.ENABLED)
                .cacheId("tenant-a")
                .build();
        Model cachedFirst = ModelRegistry.resolve("lesson:echo", cached);
        Model cachedSecond = ModelRegistry.resolve("lesson:echo", cached);
        assertThat(cachedSecond).isSameAs(cachedFirst);
    }

    @Test
    void creationContextNeverPrintsApiKeyInPlainText() {
        ModelCreationContext context = ModelCreationContext.builder()
                .apiKey("super-secret-key")
                .baseUrl("https://example.invalid")
                .build();

        assertThat(context.toString())
                .contains("[REDACTED]")
                .doesNotContain("super-secret-key");
    }
}
