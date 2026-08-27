package com.example.agentscope.sandboxproviders;

import com.example.agentscope.sandboxproviders.service.SandboxProviderCatalogService;
import io.agentscope.extensions.sandbox.agentrun.AgentRunFilesystemSpec;
import io.agentscope.extensions.sandbox.daytona.DaytonaFilesystemSpec;
import io.agentscope.extensions.sandbox.e2b.E2bFilesystemSpec;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SandboxProvidersApplication.class)
class SandboxProviderContractTest {

    @Autowired SandboxProviderCatalogService catalogService;

    @Test
    void officialSandboxSpecsCanBeConstructedWithoutStartingRemoteSandboxes() {
        assertThat(catalogService.buildExampleSpec("docker").specClass())
                .isEqualTo(DockerFilesystemSpec.class.getName());
        assertThat(catalogService.buildExampleSpec("kubernetes").specClass())
                .isEqualTo(KubernetesFilesystemSpec.class.getName());
        assertThat(catalogService.buildExampleSpec("e2b").specClass())
                .isEqualTo(E2bFilesystemSpec.class.getName());
        assertThat(catalogService.buildExampleSpec("daytona").specClass())
                .isEqualTo(DaytonaFilesystemSpec.class.getName());
        assertThat(catalogService.buildExampleSpec("agentrun").specClass())
                .isEqualTo(AgentRunFilesystemSpec.class.getName());
    }

    @Test
    void allProviderExamplesUseSessionIsolationForDeterministicLearning() {
        assertThat(catalogService.providers()).hasSize(5);
        for (String provider : new String[]{"docker", "kubernetes", "e2b", "daytona", "agentrun"}) {
            assertThat(catalogService.buildExampleSpec(provider).isolationScope())
                    .isEqualTo("SESSION");
        }
    }
}
