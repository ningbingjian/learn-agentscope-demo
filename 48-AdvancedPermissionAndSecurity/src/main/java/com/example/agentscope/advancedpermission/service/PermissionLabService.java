package com.example.agentscope.advancedpermission.service;

import com.example.agentscope.advancedpermission.tool.PermissionLabTools.GuardedDeployTool;
import com.example.agentscope.advancedpermission.tool.PermissionLabTools.MutationTool;
import com.example.agentscope.advancedpermission.tool.PermissionLabTools.ReadOnlyTool;
import com.example.agentscope.advancedpermission.tool.PermissionLabTools.SensitivePathTool;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.permission.PermissionEngine;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PermissionLabService {
    private final ReadOnlyTool readOnly = new ReadOnlyTool();
    private final MutationTool mutation = new MutationTool();
    private final GuardedDeployTool deploy = new GuardedDeployTool();
    private final SensitivePathTool pathTool = new SensitivePathTool();

    public List<String> modes() {
        return java.util.Arrays.stream(PermissionMode.values()).map(PermissionMode::getValue).toList();
    }

    public Map<String, String> matrix() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("explore.readOnly", decide(PermissionMode.EXPLORE, readOnly, Map.of("target", "service-a")));
        out.put("explore.mutation", decide(PermissionMode.EXPLORE, mutation, Map.of("target", "service-a")));
        out.put("default.mutation", decide(PermissionMode.DEFAULT, mutation, Map.of("target", "service-a")));
        out.put("dontAsk.mutation", decide(PermissionMode.DONT_ASK, mutation, Map.of("target", "service-a")));
        out.put("bypass.mutation", decide(PermissionMode.BYPASS, mutation, Map.of("target", "service-a")));
        out.put("bypass.prodDeploy", decide(PermissionMode.BYPASS, deploy, Map.of("target", "prod-api")));
        out.put("bypass.forbiddenDeploy", decide(PermissionMode.BYPASS, deploy, Map.of("target", "forbidden-root")));
        out.put("bypass.dangerousPath", decide(PermissionMode.BYPASS, pathTool, Map.of("path", ".env")));
        out.put("bypass.safePath", decide(PermissionMode.BYPASS, pathTool, Map.of("path", "notes/readme.txt")));
        return out;
    }

    public Map<String, String> rulePrecedence() {
        PermissionRule allow = new PermissionRule("deploy_service", null, PermissionBehavior.ALLOW, "lesson");
        PermissionRule ask = new PermissionRule("deploy_service", null, PermissionBehavior.ASK, "lesson");
        PermissionRule deny = new PermissionRule("deploy_service", null, PermissionBehavior.DENY, "lesson");

        PermissionContextState denyWins = PermissionContextState.builder()
                .mode(PermissionMode.BYPASS)
                .addAllowRule("deploy_service", allow)
                .addAskRule("deploy_service", ask)
                .addDenyRule("deploy_service", deny)
                .build();
        PermissionContextState askWinsOverAllow = PermissionContextState.builder()
                .mode(PermissionMode.BYPASS)
                .addAllowRule("deploy_service", allow)
                .addAskRule("deploy_service", ask)
                .build();
        PermissionContextState allowOnly = PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT)
                .addAllowRule("deploy_service", new PermissionRule(
                        "deploy_service", "prefix:dev-", PermissionBehavior.ALLOW, "lesson"))
                .build();

        return Map.of(
                "denyAskAllow", behavior(new PermissionEngine(denyWins).checkPermission(deploy, Map.of("target", "dev-api")).block()),
                "askAllow", behavior(new PermissionEngine(askWinsOverAllow).checkPermission(deploy, Map.of("target", "dev-api")).block()),
                "allowPrefix", behavior(new PermissionEngine(allowOnly).checkPermission(deploy, Map.of("target", "dev-api")).block()));
    }

    public Map<String, String> dynamicRule() {
        PermissionEngine engine = new PermissionEngine(PermissionContextState.builder().mode(PermissionMode.DEFAULT).build());
        String before = behavior(engine.checkPermission(deploy, Map.of("target", "dev-api")).block());
        engine.addRule(new PermissionRule("deploy_service", "prefix:dev-", PermissionBehavior.ALLOW, "suggested"));
        String after = behavior(engine.checkPermission(deploy, Map.of("target", "dev-api")).block());
        return Map.of("before", before, "afterAcceptedSuggestedRule", after);
    }

    public PermissionContextState switchModePreservingRules() {
        PermissionContextState base = PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT)
                .addDenyRule("deploy_service", new PermissionRule(
                        "deploy_service", "prefix:forbidden-", PermissionBehavior.DENY, "policy"))
                .build();
        return base.withMode(PermissionMode.BYPASS);
    }

    private static String decide(PermissionMode mode, io.agentscope.core.tool.ToolBase tool, Map<String, Object> input) {
        PermissionDecision decision = new PermissionEngine(PermissionContextState.builder().mode(mode).build())
                .checkPermission(tool, input).block();
        return behavior(decision);
    }

    private static String behavior(PermissionDecision decision) {
        return decision == null ? "NONE" : decision.getBehavior().name();
    }
}
