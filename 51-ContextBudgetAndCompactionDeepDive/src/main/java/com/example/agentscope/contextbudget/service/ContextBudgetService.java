package com.example.agentscope.contextbudget.service;

import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ContextBudgetService {
    public static final int MODEL_CONTEXT_WINDOW = 128_000;

    public Map<String, Object> plannedBudget() {
        Map<String, Integer> allocation = new LinkedHashMap<>();
        allocation.put("systemPrompt", 8_000);
        allocation.put("workspaceAndSkills", 8_000);
        allocation.put("toolSchemas", 12_000);
        allocation.put("memory", 8_000);
        allocation.put("rag", 20_000);
        allocation.put("conversation", 45_000);
        allocation.put("toolResults", 10_000);
        allocation.put("outputReserve", 17_000);
        int used = allocation.values().stream().mapToInt(Integer::intValue).sum();
        return Map.of(
                "contextWindow", MODEL_CONTEXT_WINDOW,
                "allocation", allocation,
                "used", used,
                "remaining", MODEL_CONTEXT_WINDOW - used,
                "rule", "Do not treat the provider context window as an unlimited bucket");
    }

    public Map<String, Object> dynamicCompaction(int contextWindow) {
        CompactionConfig config = CompactionConfig.builder().build();
        int trigger;
        int keep;
        if (contextWindow > 0) {
            trigger = contextWindow - config.getReserved();
            if (trigger <= 0) {
                trigger = Math.max(1, contextWindow / 2);
            }
            int usable = contextWindow - config.getReserved();
            keep = Math.min(
                    config.getKeepTokensMax(),
                    Math.max(config.getKeepTokensMin(), (int) (usable * config.getKeepTokensRatio())));
        } else {
            trigger = CompactionConfig.FALLBACK_TRIGGER_TOKENS;
            keep = 0;
        }
        return Map.of(
                "contextWindow", contextWindow,
                "configuredTriggerTokens", config.getTriggerTokens(),
                "reserved", config.getReserved(),
                "effectiveTriggerTokens", trigger,
                "configuredKeepTokens", config.getKeepTokens(),
                "effectiveKeepTokens", keep,
                "keepMessagesFallback", config.getKeepMessages(),
                "flushBeforeCompact", config.isFlushBeforeCompact(),
                "offloadBeforeCompact", config.isOffloadBeforeCompact(),
                "pruneEnabled", config.getPruneConfig() != null);
    }

    public Map<String, Object> evictionDefaults() {
        ToolResultEvictionConfig config = ToolResultEvictionConfig.defaults();
        return Map.of(
                "maxResultChars", config.getMaxResultChars(),
                "previewChars", config.getPreviewChars(),
                "evictionPath", config.getEvictionPath(),
                "excludedTools", config.getExcludedToolNames(),
                "width", "ToolResultEviction",
                "depth", "ConversationCompaction",
                "workspaceBudget", "HarnessAgent.maxContextTokens controls rendered workspace context only");
    }
}
