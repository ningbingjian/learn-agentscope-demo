package com.example.agentscope.evaluation.service;

import com.example.agentscope.evaluation.model.EvaluationModel;
import com.example.agentscope.evaluation.tool.RecordingWeatherTools;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {
    private final ReActAgent agent;
    private final EvaluationModel model;
    private final RecordingWeatherTools weatherTools;
    private final ObjectMapper objectMapper;

    public EvaluationService(ReActAgent agent, EvaluationModel model, RecordingWeatherTools weatherTools, ObjectMapper objectMapper) {
        this.agent = agent;
        this.model = model;
        this.weatherTools = weatherTools;
        this.objectMapper = objectMapper;
    }

    public List<EvalCase> dataset() {
        try (InputStream in = new ClassPathResource("eval-cases.json").getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<EvalCase>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load evaluation dataset", e);
        }
    }

    public EvalReport runAll() {
        List<EvalResult> results = dataset().stream().map(this::runCase).toList();
        int passed = (int) results.stream().filter(EvalResult::passed).count();
        long toolCases = results.stream().filter(r -> r.expectedTool() != null).count();
        long toolCorrect = results.stream().filter(r -> r.expectedTool() != null && r.toolCorrect()).count();
        long argCorrect = results.stream().filter(r -> r.expectedTool() != null && r.argumentCorrect()).count();
        int totalTokens = results.stream().mapToInt(EvalResult::totalTokens).sum();
        double cost = results.stream().mapToDouble(EvalResult::estimatedCostUsd).sum();
        double passRate = results.isEmpty() ? 0 : (double) passed / results.size();
        double toolAccuracy = toolCases == 0 ? 1.0 : (double) toolCorrect / toolCases;
        double argumentAccuracy = toolCases == 0 ? 1.0 : (double) argCorrect / toolCases;
        return new EvalReport(
                results.size(), passed, passRate, toolAccuracy, argumentAccuracy,
                totalTokens, cost, passRate >= 1.0 && toolAccuracy >= 1.0 && argumentAccuracy >= 1.0,
                results);
    }

    public Map<String, Object> philosophy() {
        return Map.of(
                "unit", "pure helpers and deterministic tools",
                "contract", "Model/Tool/Middleware boundaries",
                "scenario", "real ReActAgent + dataset",
                "metrics", "tool selection, arguments, final answer, tokens, latency, cost",
                "gate", "block a model/prompt rollout when regression thresholds are missed",
                "versionBoundary", "AgentScope Java 2.0.1 has no dedicated evaluator module, so evaluation orchestration stays application-layer");
    }

    private EvalResult runCase(EvalCase testCase) {
        weatherTools.reset();
        model.resetUsage();
        long start = System.nanoTime();
        RuntimeContext ctx = RuntimeContext.builder().userId("eval").sessionId(testCase.id()).build();
        Msg reply = agent.call(new UserMessage(testCase.input()), ctx).block();
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        String actualTool = weatherTools.calls() > 0 ? "get_weather" : null;
        boolean toolCorrect = java.util.Objects.equals(testCase.expectedTool(), actualTool);
        boolean argumentCorrect = testCase.expectedTool() == null
                || java.util.Objects.equals(testCase.expectedCity(), weatherTools.lastCity());
        String finalText = reply == null ? "" : reply.getTextContent();
        boolean finalCorrect = finalText.contains(testCase.expectedTextContains());
        int in = model.inputTokens();
        int out = model.outputTokens();
        int total = in + out;
        double estimatedCost = (in / 1000.0) * 0.001 + (out / 1000.0) * 0.002;
        boolean passed = toolCorrect && argumentCorrect && finalCorrect && total <= testCase.maxTotalTokens();

        return new EvalResult(
                testCase.id(), testCase.expectedTool(), actualTool, toolCorrect, argumentCorrect,
                finalCorrect, total, latencyMs, estimatedCost, passed, finalText);
    }

    public record EvalCase(
            String id,
            String input,
            String expectedTool,
            String expectedCity,
            String expectedTextContains,
            int maxTotalTokens) {}

    public record EvalResult(
            String id,
            String expectedTool,
            String actualTool,
            boolean toolCorrect,
            boolean argumentCorrect,
            boolean finalCorrect,
            int totalTokens,
            long latencyMs,
            double estimatedCostUsd,
            boolean passed,
            String finalText) {}

    public record EvalReport(
            int totalCases,
            int passedCases,
            double passRate,
            double toolSelectionAccuracy,
            double argumentAccuracy,
            int totalTokens,
            double estimatedCostUsd,
            boolean gatePassed,
            List<EvalResult> results) {}
}
