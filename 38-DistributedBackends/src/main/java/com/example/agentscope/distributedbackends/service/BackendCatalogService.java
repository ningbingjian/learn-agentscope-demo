package com.example.agentscope.distributedbackends.service;

import io.agentscope.extensions.mysql.MysqlDistributedStore;
import io.agentscope.extensions.oss.OssDistributedStore;
import io.agentscope.extensions.redis.RedisDistributedStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BackendCatalogService {

    public List<BackendInfo> backends() {
        return List.of(
                new BackendInfo(
                        "redis",
                        "agentscope-extensions-redis",
                        RedisDistributedStore.class.getName(),
                        true, true, true, true,
                        "低延迟、多副本、状态与锁优先"),
                new BackendInfo(
                        "mysql",
                        "agentscope-extensions-mysql",
                        MysqlDistributedStore.class.getName(),
                        true, true, true, true,
                        "已有关系型数据库、SQL 审计"),
                new BackendInfo(
                        "oss",
                        "agentscope-extensions-oss",
                        OssDistributedStore.class.getName(),
                        true, true, true, false,
                        "大容量工作区与 Sandbox Snapshot"));
    }

    public record BackendInfo(
            String id,
            String artifact,
            String distributedStoreClass,
            boolean agentStateStore,
            boolean baseStore,
            boolean snapshot,
            boolean executionGuard,
            String bestFor) {
    }
}
