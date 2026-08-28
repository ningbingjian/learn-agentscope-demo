package com.example.agentscope.advancedpermission;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.advancedpermission.service.PermissionLabService;
import io.agentscope.core.permission.PermissionMode;
import org.junit.jupiter.api.Test;

class AdvancedPermissionContractTest {
    private final PermissionLabService service = new PermissionLabService();

    @Test
    void allFivePermissionModesAreCovered() {
        assertThat(service.modes()).containsExactly(
                "default", "accept_edits", "explore", "bypass", "dont_ask");
    }

    @Test
    void modeAndBuiltInSafetyChecksProduceExpectedDecisions() {
        assertThat(service.matrix())
                .containsEntry("explore.readOnly", "ALLOW")
                .containsEntry("explore.mutation", "DENY")
                .containsEntry("acceptEdits.readOnly", "ALLOW")
                .containsEntry("acceptEdits.mutation", "ASK")
                .containsEntry("default.mutation", "ASK")
                .containsEntry("dontAsk.mutation", "DENY")
                .containsEntry("bypass.mutation", "ALLOW")
                .containsEntry("bypass.prodDeploy", "ASK")
                .containsEntry("bypass.forbiddenDeploy", "DENY")
                .containsEntry("bypass.dangerousPath", "ASK")
                .containsEntry("bypass.safePath", "ALLOW");
    }

    @Test
    void ruleOrderIsDenyThenAskThenAllowAndRulesCanBeAddedAtRuntime() {
        assertThat(service.rulePrecedence())
                .containsEntry("denyAskAllow", "DENY")
                .containsEntry("askAllow", "ASK")
                .containsEntry("allowPrefix", "ALLOW");
        assertThat(service.dynamicRule())
                .containsEntry("before", "ASK")
                .containsEntry("afterAcceptedSuggestedRule", "ALLOW");
    }

    @Test
    void switchingModePreservesConfiguredRules() {
        var switched = service.switchModePreservingRules();
        assertThat(switched.getMode()).isEqualTo(PermissionMode.BYPASS);
        assertThat(switched.getDenyRules()).containsKey("deploy_service");
    }
}
