package com.example.agentscope.kubernetes.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.clients.jedis.JedisPooled;

@Configuration(proxyBeanMethods = false)
@Profile("distributed")
public class RedisConfiguration {

    @Bean(destroyMethod = "close")
    JedisPooled productionJedis(@Value("${deployment.redis-url}") String redisUrl) {
        if (redisUrl == null || redisUrl.isBlank()) {
            throw new IllegalStateException("distributed profile requires deployment.redis-url / REDIS_URL");
        }
        return new JedisPooled(redisUrl);
    }
}
