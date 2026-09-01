package com.example.agentscope.distributedstateandstorage.store;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.State;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Teaching-only state-store test double used to model an externally shared backend.
 *
 * <p>AgentScope intentionally rejects {@link InMemoryAgentStateStore} when a
 * {@code RemoteFilesystemSpec} is selected, because the built-in in-memory store is
 * single-process only. This wrapper lets two HarnessAgent instances in the same JVM
 * exercise the distributed-store wiring without pretending the built-in local store
 * is production-safe.
 *
 * <p>Production deployments must replace this class with a real shared implementation
 * such as RedisAgentStateStore or MysqlAgentStateStore.
 */
public final class DemoSharedAgentStateStore implements AgentStateStore {

    private final InMemoryAgentStateStore delegate = new InMemoryAgentStateStore();

    @Override
    public void save(String userId, String sessionId, String key, State value) {
        delegate.save(userId, sessionId, key, value);
    }

    @Override
    public void save(
            String userId,
            String sessionId,
            String key,
            List<? extends State> values
    ) {
        delegate.save(userId, sessionId, key, values);
    }

    @Override
    public <T extends State> Optional<T> get(
            String userId,
            String sessionId,
            String key,
            Class<T> type
    ) {
        return delegate.get(userId, sessionId, key, type);
    }

    @Override
    public <T extends State> List<T> getList(
            String userId,
            String sessionId,
            String key,
            Class<T> itemType
    ) {
        return delegate.getList(userId, sessionId, key, itemType);
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return delegate.exists(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId) {
        delegate.delete(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        delegate.delete(userId, sessionId, key);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        return delegate.listSessionIds(userId);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
