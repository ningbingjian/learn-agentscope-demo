package com.example.agentscope.filesystemandsandbox;

import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilesystemSpecContractTest {

    @Test
    void localSpecUsesRootedModeAndUserIsolation() {
        LocalFilesystemSpec local = new LocalFilesystemSpec()
                .mode(LocalFsMode.ROOTED)
                .isolationScope(IsolationScope.USER)
                .inheritEnv(false);

        assertThat(local.getMode()).isEqualTo(LocalFsMode.ROOTED);
        assertThat(local.getIsolationScope()).isEqualTo(IsolationScope.USER);
    }

    @Test
    void dockerSpecCanBeConfiguredWithoutStartingDocker() {
        DockerFilesystemSpec docker = new DockerFilesystemSpec()
                .image("python:3.12-slim")
                .memorySizeBytes(256L * 1024 * 1024)
                .cpuCount(1L);
        docker.isolationScope(IsolationScope.SESSION);

        assertThat(docker.getIsolationScope()).isEqualTo(IsolationScope.SESSION);
    }
}
