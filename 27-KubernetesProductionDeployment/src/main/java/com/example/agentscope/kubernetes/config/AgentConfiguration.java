package com.example.agentscope.kubernetes.config;

import io.agentscope.core.model.Model;
import io.agentscope.core.shutdown.GracefulShutdownConfig;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.shutdown.PartialReasoningPolicy;
import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

import java.nio.file.Paths;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    @Bean
    GracefulShutdownManager gracefulShutdownManager() {
        GracefulShutdownManager manager = GracefulShutdownManager.getInstance();
        manager.setConfig(new GracefulShutdownConfig(
                Duration.ofSeconds(30),
                PartialReasoningPolicy.SAVE
        ));
        return manager;
    }

    @Bean(destroyMethod = "close")
    HarnessAgent productionAgent(Model model, ObjectProvider<JedisPooled> jedisProvider) {
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("kubernetes-production-agent")
                .sysPrompt("你是运行在 Kubernetes 多副本服务中的 AgentScope 助手。")
                .model(model)
                .workspace(Paths.get(".agentscope/workspace"));

        JedisPooled jedis = jedisProvider.getIfAvailable();
        if (jedis != null) {
            RedisDistributedStore distributedStore = RedisDistributedStore.fromJedis(
                    jedis,
                    "learn-agentscope:"
            );
            builder.distributedStore(distributedStore)
                    .filesystem(new RemoteFilesystemSpec()
                            .isolationScope(IsolationScope.USER));
        }

        return builder.build();
    }
}
