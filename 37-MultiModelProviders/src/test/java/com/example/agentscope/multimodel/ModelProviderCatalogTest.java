package com.example.agentscope.multimodel;

import com.example.agentscope.multimodel.service.ModelProviderCatalogService;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.model.spi.ModelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MultiModelProvidersApplication.class)
class ModelProviderCatalogTest {

    @Autowired
    ModelProviderCatalogService catalogService;

    @BeforeEach
    void reloadProviders() {
        ModelRegistry.reloadProviders();
    }

    @Test
    void serviceLoaderDiscoversOfficialProviderExtensions() {
        Set<String> providerIds = ServiceLoader.load(ModelProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(ModelProvider::providerId)
                .collect(Collectors.toSet());

        assertThat(providerIds).contains(
                "openai", "deepseek", "kimi", "glm", "minimax",
                "dashscope", "gemini", "anthropic", "ollama");
    }

    @Test
    void modelRegistryCanRouteProviderModelIdsWithoutCreatingModels() {
        assertThat(ModelRegistry.canResolve("openai:gpt-4.1-mini")).isTrue();
        assertThat(ModelRegistry.canResolve("deepseek:deepseek-chat")).isTrue();
        assertThat(ModelRegistry.canResolve("kimi:moonshot-v1-8k")).isTrue();
        assertThat(ModelRegistry.canResolve("glm:glm-4")).isTrue();
        assertThat(ModelRegistry.canResolve("minimax:MiniMax-M2.1")).isTrue();
        assertThat(ModelRegistry.canResolve("dashscope:qwen-plus")).isTrue();
        assertThat(ModelRegistry.canResolve("gemini:gemini-2.0-flash")).isTrue();
        assertThat(ModelRegistry.canResolve("anthropic:claude-sonnet-4-20250514")).isTrue();
        assertThat(ModelRegistry.canResolve("ollama:llama3.2")).isTrue();
    }

    @Test
    void catalogShowsSpiAndRegistryAsTwoSeparateChecks() {
        assertThat(catalogService.providers())
                .allSatisfy(provider -> {
                    assertThat(provider.discoveredBySpi()).isTrue();
                    assertThat(provider.resolvableByModelRegistry()).isTrue();
                    assertThat(provider.providerClass()).isNotBlank();
                });
    }
}
