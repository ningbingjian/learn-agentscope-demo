package com.example.agentscope.distributedbackends;

import com.example.agentscope.distributedbackends.service.BackendCatalogService;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DistributedBackendsApplication.class)
class DistributedBackendsContractTest {

    private static final List<String> NS = List.of("test", "cas");

    @Autowired BaseStore jdbcBaseStore;
    @Autowired DistributedStore lessonMixedStore;
    @Autowired BackendCatalogService catalogService;

    @Test
    void jdbcStoreRunsOnH2AndSupportsAtomicVersionCas() {
        String key = "counter";
        jdbcBaseStore.delete(NS, key);

        boolean created = jdbcBaseStore.putIfVersion(NS, key, Map.of("value", 1), 0);
        assertThat(created).isTrue();

        StoreItem first = jdbcBaseStore.get(NS, key);
        assertThat(first).isNotNull();
        assertThat(first.version()).isGreaterThanOrEqualTo(1);
        assertThat(first.value()).containsEntry("value", 1);

        boolean updated = jdbcBaseStore.putIfVersion(
                NS, key, Map.of("value", 2), first.version());
        boolean staleWriter = jdbcBaseStore.putIfVersion(
                NS, key, Map.of("value", 999), first.version());

        assertThat(updated).isTrue();
        assertThat(staleWriter).isFalse();

        StoreItem latest = jdbcBaseStore.get(NS, key);
        assertThat(latest.value()).containsEntry("value", 2);
        assertThat(latest.version()).isGreaterThan(first.version());
    }

    @Test
    void distributedStoreCanComposeComponentsFromDifferentBackends() {
        assertThat(lessonMixedStore.agentStateStore()).isNotNull();
        assertThat(lessonMixedStore.baseStore()).isSameAs(jdbcBaseStore);
        assertThat(lessonMixedStore.sandboxSnapshotSpec().getClass().getSimpleName())
                .isEqualTo("JdbcSnapshotSpec");
        assertThat(lessonMixedStore.sandboxExecutionGuard()).isNotNull();
    }

    @Test
    void officialRedisMysqlAndOssBackendsAreOnClasspath() {
        assertThat(catalogService.backends())
                .extracting(BackendCatalogService.BackendInfo::id)
                .containsExactly("redis", "mysql", "oss");
    }
}
