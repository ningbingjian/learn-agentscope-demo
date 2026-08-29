package com.example.agentscope.security.service;

import com.example.agentscope.security.tool.SecurityLabTools.SensitiveWriteTool;
import com.example.agentscope.security.tool.SecurityLabTools.ToolSurface;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionEngine;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.skill.curator.SkillSecurityScanner;
import io.agentscope.harness.agent.tools.ToolFilter;
import io.agentscope.harness.agent.tools.ToolsConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service
public class SecurityArchitectureService {

    public Map<String, Object> scanSkill(String content, String trustLevel) {
        SkillSecurityScanner.TrustLevel trust = SkillSecurityScanner.TrustLevel.valueOf(trustLevel.toUpperCase());
        var result = SkillSecurityScanner.scanSingleFile("SKILL.md", content);
        return Map.of(
                "trust", trust.name(),
                "verdict", result.verdict().name(),
                "allowed", SkillSecurityScanner.shouldAllow(trust, result.verdict()),
                "findingCount", result.findings().size(),
                "categories", result.findings().stream().map(f -> f.category().name()).distinct().toList(),
                "report", result.reportText());
    }

    public Map<String, Object> toolSurfaceDemo() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ToolSurface());
        var before = new TreeSet<>(toolkit.getToolNames());

        ToolsConfig cfg = new ToolsConfig();
        cfg.setAllow(List.of("read_status"));
        cfg.setDeny(List.of("dangerous_shell"));
        ToolFilter.apply(toolkit, cfg);
        var after = new TreeSet<>(toolkit.getToolNames());

        return Map.of(
                "before", before,
                "after", after,
                "principle", "minimize the tool surface before schemas are exposed to the model");
    }

    public Map<String, Object> permissionDemo(String path) {
        SensitiveWriteTool tool = new SensitiveWriteTool();
        PermissionEngine engine = new PermissionEngine(
                PermissionContextState.builder().mode(PermissionMode.BYPASS).build());
        var decision = engine.checkPermission(tool, Map.of("path", path)).block();
        return Map.of(
                "path", path,
                "mode", "BYPASS",
                "decision", decision.getBehavior().name(),
                "reason", String.valueOf(decision.getDecisionReason()),
                "lesson", "BYPASS is not allowed to defeat a safety-tagged tool self-check");
    }

    public Map<String, Object> retrievedContentBoundary(String retrievedText) {
        var scan = SkillSecurityScanner.scanSingleFile("retrieved-document.txt", retrievedText);
        boolean injectionMarker = scan.findings().stream()
                .anyMatch(f -> f.category() == SkillSecurityScanner.Category.INJECTION);
        return Map.of(
                "sourceTrust", "UNTRUSTED_DATA",
                "instructionAuthority", "NONE",
                "injectionMarkerDetected", injectionMarker,
                "rule", "retrieved RAG/web/PDF content is evidence/data, never higher-priority instruction",
                "scannerNote", "regex scanning is defense-in-depth, not the security boundary");
    }

    public Map<String, Object> architecture() {
        Map<String, Object> layers = new LinkedHashMap<>();
        layers.put("1-input", "treat user/web/RAG/file content as untrusted data");
        layers.put("2-context", "separate trusted system policy from retrieved content");
        layers.put("3-tool-surface", "allowlist tools/MCP functions before exposing schemas");
        layers.put("4-permission", "evaluate side effects and sensitive paths before execution");
        layers.put("5-sandbox", "isolate shell/filesystem/process/network blast radius");
        layers.put("6-secrets", "do not place long-lived credentials in prompts, workspaces, or tool output");
        layers.put("7-egress", "network allowlist and SSRF controls belong outside model reasoning");
        layers.put("8-audit", "trace Tool decisions, HITL approvals, external execution and security findings");
        layers.put("9-eval", "run adversarial prompt/tool/security regression datasets before rollout");
        return layers;
    }
}
