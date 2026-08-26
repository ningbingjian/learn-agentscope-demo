package com.example.agentscope.filesystemandsandbox.config;

import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    boolean sandboxDemoEnabled(@Value("${demo.sandbox.enabled:false}") boolean enabled) {
        return enabled;
    }

    @Bean(destroyMethod = "close")
    HarnessAgent filesystemAgent(
            Model model,
            boolean sandboxDemoEnabled,
            @Value("${demo.sandbox.image:python:3.12-slim}") String sandboxImage
    ) {
        var builder = HarnessAgent.builder()
                .name("filesystem-sandbox-agent")
                .sysPrompt("你是文件系统学习助手。只在当前项目与 workspace 范围内操作文件；"
                        + "需要执行命令时先解释目的，再调用 execute。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"))
                .disableMemoryHooks()
                .disableCompaction()
                .disableSubagents();

        if (sandboxDemoEnabled) {
            DockerFilesystemSpec docker = new DockerFilesystemSpec()
                    .image(sandboxImage)
                    .memorySizeBytes(512L * 1024 * 1024)
                    .cpuCount(1L)
                    .workspaceRoot("/workspace");
            docker.isolationScope(IsolationScope.SESSION);
            builder.filesystem(docker);
        } else {
            LocalFilesystemSpec local = new LocalFilesystemSpec()
                    .mode(LocalFsMode.ROOTED)
                    .isolationScope(IsolationScope.USER)
                    .inheritEnv(false)
                    .executeTimeoutSeconds(30)
                    .maxOutputBytes(100_000);
            builder.filesystem(local);
        }

        return builder.build();
    }
}
