package com.example.agentscope.sandboxproviders.service;

import io.agentscope.extensions.sandbox.agentrun.AgentRunFilesystemSpec;
import io.agentscope.extensions.sandbox.daytona.DaytonaFilesystemSpec;
import io.agentscope.extensions.sandbox.e2b.E2bFilesystemSpec;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesFilesystemSpec;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class SandboxProviderCatalogService {

    public List<ProviderInfo> providers() {
        return List.of(
                new ProviderInfo(
                        "docker", "agentscope-harness",
                        DockerFilesystemSpec.class.getName(),
                        "Docker daemon", "无云凭证",
                        "本地开发、单机、可信环境"),
                new ProviderInfo(
                        "kubernetes", "agentscope-extensions-sandbox-kubernetes",
                        KubernetesFilesystemSpec.class.getName(),
                        "kubernetes-sigs/agent-sandbox", "KubeConfig / ServiceAccount",
                        "自建 K8s、SandboxClaim / WarmPool"),
                new ProviderInfo(
                        "e2b", "agentscope-extensions-sandbox-e2b",
                        E2bFilesystemSpec.class.getName(),
                        "E2B control plane", "E2B API Key",
                        "托管开发沙箱、平台原生快照"),
                new ProviderInfo(
                        "daytona", "agentscope-extensions-sandbox-daytona",
                        DaytonaFilesystemSpec.class.getName(),
                        "Daytona control plane/toolbox", "Daytona API Key",
                        "通用托管 Sandbox HTTP API"),
                new ProviderInfo(
                        "agentrun", "agentscope-extensions-sandbox-agentrun",
                        AgentRunFilesystemSpec.class.getName(),
                        "Alibaba Cloud AgentRun", "AgentRun API Key / RAM",
                        "阿里云托管沙箱、NAS/OSS 挂载"));
    }

    public SpecView buildExampleSpec(String provider) {
        SandboxFilesystemSpec spec = switch (provider.toLowerCase(Locale.ROOT)) {
            case "docker" -> dockerSpec();
            case "kubernetes", "k8s" -> kubernetesSpec();
            case "e2b" -> e2bSpec();
            case "daytona" -> daytonaSpec();
            case "agentrun" -> agentRunSpec();
            default -> throw new IllegalArgumentException("Unknown sandbox provider: " + provider);
        };
        return new SpecView(provider.toLowerCase(Locale.ROOT), spec.getClass().getName(), "SESSION");
    }

    SandboxFilesystemSpec dockerSpec() {
        DockerFilesystemSpec spec = new DockerFilesystemSpec()
                .image("ubuntu:24.04")
                .memorySizeBytes(512L * 1024 * 1024)
                .cpuCount(1L)
                .workspaceRoot("/workspace");
        spec.isolationScope(IsolationScope.SESSION);
        return spec;
    }

    SandboxFilesystemSpec kubernetesSpec() {
        KubernetesFilesystemSpec spec = new KubernetesFilesystemSpec()
                .namespace("agentscope")
                .warmPoolName("agentscope-warm-pool")
                .workspaceRoot("/workspace")
                .fileApiBaseDir("/workspace")
                .serverPort(8888);
        spec.isolationScope(IsolationScope.SESSION);
        return spec;
    }

    SandboxFilesystemSpec e2bSpec() {
        E2bFilesystemSpec spec = new E2bFilesystemSpec()
                .templateId("base")
                .workspaceRoot("/workspace")
                .sandboxTimeoutSeconds(600)
                .connectTimeoutSeconds(30)
                .readTimeoutSeconds(60)
                .maxRetries(2);
        spec.isolationScope(IsolationScope.SESSION);
        return spec;
    }

    SandboxFilesystemSpec daytonaSpec() {
        DaytonaFilesystemSpec spec = new DaytonaFilesystemSpec()
                .image("ubuntu:24.04")
                .cpu(1)
                .memory(2)
                .disk(5)
                .workspaceRoot("/workspace");
        spec.isolationScope(IsolationScope.SESSION);
        return spec;
    }

    SandboxFilesystemSpec agentRunSpec() {
        AgentRunFilesystemSpec spec = new AgentRunFilesystemSpec()
                .region("cn-hangzhou")
                .templateName("agentscope-demo")
                .workspaceRoot("/workspace")
                .sandboxIdleTimeoutSeconds(600)
                .connectTimeoutSeconds(30)
                .readTimeoutSeconds(60)
                .maxRetries(2);
        spec.isolationScope(IsolationScope.SESSION);
        return spec;
    }

    public record ProviderInfo(
            String id,
            String artifact,
            String specClass,
            String controlPlane,
            String credential,
            String bestFor) {
    }

    public record SpecView(String provider, String specClass, String isolationScope) {
    }
}
