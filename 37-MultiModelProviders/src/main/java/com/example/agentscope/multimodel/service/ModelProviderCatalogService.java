package com.example.agentscope.multimodel.service;

import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.model.spi.ModelProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

@Service
public class ModelProviderCatalogService {

    private static final Map<String, ProviderDescriptor> DOCUMENTED = documentedProviders();

    public List<ProviderView> providers() {
        Map<String, String> discovered = new TreeMap<>();
        ServiceLoader.load(ModelProvider.class)
                .forEach(provider -> discovered.putIfAbsent(
                        provider.providerId(), provider.getClass().getName()));

        return DOCUMENTED.values().stream()
                .map(descriptor -> new ProviderView(
                        descriptor.id(),
                        descriptor.artifact(),
                        descriptor.exampleModelId(),
                        descriptor.credentialEnvironment(),
                        discovered.containsKey(descriptor.id()),
                        discovered.get(descriptor.id()),
                        ModelRegistry.canResolve(descriptor.exampleModelId())))
                .toList();
    }

    public ResolveCheck check(String modelId) {
        return new ResolveCheck(modelId, ModelRegistry.canResolve(modelId));
    }

    private static Map<String, ProviderDescriptor> documentedProviders() {
        Map<String, ProviderDescriptor> values = new LinkedHashMap<>();
        values.put("openai", new ProviderDescriptor(
                "openai", "agentscope-extensions-model-openai",
                "openai:gpt-4.1-mini", "OPENAI_API_KEY"));
        values.put("deepseek", new ProviderDescriptor(
                "deepseek", "agentscope-extensions-model-openai",
                "deepseek:deepseek-chat", "DEEPSEEK_API_KEY"));
        values.put("kimi", new ProviderDescriptor(
                "kimi", "agentscope-extensions-model-openai",
                "kimi:moonshot-v1-8k", "MOONSHOT_API_KEY / KIMI_API_KEY"));
        values.put("glm", new ProviderDescriptor(
                "glm", "agentscope-extensions-model-openai",
                "glm:glm-4", "ZAI_API_KEY / GLM_API_KEY / ZHIPUAI_API_KEY"));
        values.put("minimax", new ProviderDescriptor(
                "minimax", "agentscope-extensions-model-openai",
                "minimax:MiniMax-M2.1", "MINIMAX_API_KEY"));
        values.put("dashscope", new ProviderDescriptor(
                "dashscope", "agentscope-extensions-model-dashscope",
                "dashscope:qwen-plus", "DASHSCOPE_API_KEY"));
        values.put("gemini", new ProviderDescriptor(
                "gemini", "agentscope-extensions-model-gemini",
                "gemini:gemini-2.0-flash", "GEMINI_API_KEY"));
        values.put("anthropic", new ProviderDescriptor(
                "anthropic", "agentscope-extensions-model-anthropic",
                "anthropic:claude-sonnet-4-20250514", "ANTHROPIC_API_KEY"));
        values.put("ollama", new ProviderDescriptor(
                "ollama", "agentscope-extensions-model-ollama",
                "ollama:llama3.2", "OLLAMA_BASE_URL (optional)"));
        return Map.copyOf(values);
    }

    private record ProviderDescriptor(
            String id,
            String artifact,
            String exampleModelId,
            String credentialEnvironment) {
    }

    public record ProviderView(
            String id,
            String artifact,
            String exampleModelId,
            String credentialEnvironment,
            boolean discoveredBySpi,
            String providerClass,
            boolean resolvableByModelRegistry) {
    }

    public record ResolveCheck(String modelId, boolean resolvable) {
    }
}
