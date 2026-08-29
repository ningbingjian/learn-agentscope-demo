package com.example.agentscope.consistency.config;

import com.example.agentscope.consistency.model.ConcurrentProbeModel;
import io.agentscope.core.ReActAgent;
import io.agentscope.extensions.mysql.store.JdbcStore;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration(proxyBeanMethods = false)
public class ConsistencyConfiguration {

    @Bean
    DataSource lesson52DataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:agentscope_consistency;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    @Bean
    JdbcStore lesson52Store(DataSource lesson52DataSource) {
        return JdbcStore.builder(lesson52DataSource)
                .tableName("lesson52_consistency")
                .initializeSchema(true)
                .build();
    }

    @Bean
    ConcurrentProbeModel concurrentProbeModel() {
        return new ConcurrentProbeModel();
    }

    @Bean(destroyMethod = "close")
    ReActAgent concurrencyAgent(ConcurrentProbeModel concurrentProbeModel) {
        return ReActAgent.builder()
                .name("lesson52-agent")
                .sysPrompt("Return a short deterministic answer.")
                .model(concurrentProbeModel)
                .build();
    }
}
