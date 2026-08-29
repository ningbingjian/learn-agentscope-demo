package com.example.agentscope.production.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProductionArchitectureService {

    public Map<String, Object> architecture() {
        return Map.of(
                "requestPath", List.of(
                        "API/Gateway", "Idempotency", "Application Retrieval", "RuntimeContext",
                        "Agent Runtime", "Model/Tool", "State", "Telemetry", "Eval/Security"),
                "stateRule", "same session serialized locally; cross-pod writes require shared store/CAS/lock strategy",
                "trustRule", "retrieved data and tool output are data, not instruction authority",
                "releaseRule", "model/prompt/tool changes should pass deterministic evaluation gates before rollout");
    }

    public List<Decision> decisions() {
        return List.of(
                new Decision("Model", "deterministic local Model", "Model Gateway + real provider", "timeouts, retries, fallback, cost policy"),
                new Decision("Agent state", "ReActAgent session cache", "Redis/MySQL AgentStateStore", "multi-pod session consistency"),
                new Decision("Idempotency", "H2 JdbcStore", "MySQL/Redis durable store", "webhook/request/wakeup replay protection"),
                new Decision("Retrieval", "in-memory application retriever", "vector DB / Dify / RAGFlow / managed RAG", "retrieval stays application-layer"),
                new Decision("Tools", "local @Tool", "business API + MCP allowlist", "least privilege and schema review"),
                new Decision("Sandbox", "not needed by local read-only tool", "Kubernetes/E2B/Daytona/AgentRun", "isolate untrusted code and shell"),
                new Decision("Observability", "Middleware counters", "OpenTelemetry + metrics + Studio", "trace model/tool/session without leaking secrets"),
                new Decision("Evaluation", "deterministic local gate", "CI/CD regression dataset", "block bad model/prompt/tool rollouts"),
                new Decision("Secrets", "none in demo", "Secret Manager/workload identity", "never expose credential value to model"),
                new Decision("Deployment", "single local JVM", "Kubernetes replicas + HPA + PDB", "readiness, graceful shutdown, capacity planning"));
    }

    public Map<String, Object> readiness() {
        return Map.of(
                "localDemoReady", true,
                "productionReadyByDefault", false,
                "mustExternalize", List.of("model credentials", "distributed state", "idempotency store", "telemetry exporter"),
                "mustEnforce", List.of("tool allowlist", "permission policy", "sandbox/egress policy", "secret isolation", "eval gate"),
                "mustOperate", List.of("SLO", "alerts", "capacity", "graceful shutdown", "rollback", "audit"));
    }

    public record Decision(String layer, String localDemo, String productionTarget, String reason) {}
}
