package com.example.agentscope.distributedbackends.config;

import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.extensions.mysql.snapshot.JdbcSnapshotSpec;
import io.agentscope.extensions.mysql.store.JdbcStore;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class StoreConfiguration {

    @Bean
    DataSource lessonDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:agentscope_distributed;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    @Bean
    BaseStore jdbcBaseStore(DataSource lessonDataSource) {
        return JdbcStore.builder(lessonDataSource)
                .initializeSchema(true)
                .tableName("lesson_workspace_store")
                .build();
    }

    @Bean
    SandboxSnapshotSpec jdbcSnapshotSpec(DataSource lessonDataSource) {
        return new JdbcSnapshotSpec(lessonDataSource, "lesson_sandbox_snapshots");
    }

    @Bean
    DistributedStore lessonMixedStore(
            BaseStore jdbcBaseStore,
            SandboxSnapshotSpec jdbcSnapshotSpec) {
        return DistributedStore.builder()
                .agentStateStore(new InMemoryAgentStateStore())
                .baseStore(jdbcBaseStore)
                .sandboxSnapshotSpec(jdbcSnapshotSpec)
                .build();
    }
}
