package com.example.agentscope.distributedbackends.web;

import com.example.agentscope.distributedbackends.service.BackendCatalogService;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/distributed")
public class DistributedBackendsController {

    private static final List<String> LESSON_NAMESPACE = List.of("lesson", "workspace");

    private final BaseStore jdbcBaseStore;
    private final DistributedStore lessonMixedStore;
    private final BackendCatalogService catalogService;

    public DistributedBackendsController(
            BaseStore jdbcBaseStore,
            DistributedStore lessonMixedStore,
            BackendCatalogService catalogService) {
        this.jdbcBaseStore = jdbcBaseStore;
        this.lessonMixedStore = lessonMixedStore;
        this.catalogService = catalogService;
    }

    @GetMapping("/backends")
    public List<BackendCatalogService.BackendInfo> backends() {
        return catalogService.backends();
    }

    @PostMapping("/jdbc/{key}")
    public StoreView put(@PathVariable String key, @RequestBody Map<String, Object> value) {
        jdbcBaseStore.put(LESSON_NAMESPACE, key, value);
        return toView(jdbcBaseStore.get(LESSON_NAMESPACE, key));
    }

    @GetMapping("/jdbc/{key}")
    public StoreView get(@PathVariable String key) {
        return toView(jdbcBaseStore.get(LESSON_NAMESPACE, key));
    }

    @PutMapping("/jdbc/{key}/cas")
    public CasResult compareAndSwap(@PathVariable String key, @RequestBody CasRequest request) {
        boolean updated = jdbcBaseStore.putIfVersion(
                LESSON_NAMESPACE, key, request.value(), request.expectedVersion());
        return new CasResult(updated, toView(jdbcBaseStore.get(LESSON_NAMESPACE, key)));
    }

    @GetMapping("/mixed")
    public MixedStoreView mixedStore() {
        return new MixedStoreView(
                lessonMixedStore.agentStateStore().getClass().getName(),
                lessonMixedStore.baseStore().getClass().getName(),
                lessonMixedStore.sandboxSnapshotSpec().getClass().getName(),
                lessonMixedStore.sandboxExecutionGuard().getClass().getName());
    }

    private static StoreView toView(StoreItem item) {
        if (item == null) {
            return null;
        }
        return new StoreView(item.key(), item.value(), item.version());
    }

    public record StoreView(String key, Map<String, Object> value, long version) {
    }

    public record CasRequest(long expectedVersion, Map<String, Object> value) {
    }

    public record CasResult(boolean updated, StoreView current) {
    }

    public record MixedStoreView(
            String agentStateStore,
            String baseStore,
            String sandboxSnapshotSpec,
            String sandboxExecutionGuard) {
    }
}
