package com.example.agentscope.asynctool.bus;

import io.agentscope.harness.agent.bus.AsyncToolRecord;
import io.agentscope.harness.agent.bus.AsyncToolRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;

public class LessonAsyncToolRegistry implements AsyncToolRegistry {
    private final Map<String, Snapshot> records = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> register(AsyncToolRecord record) {
        return Mono.fromRunnable(() -> records.put(record.id(), new Snapshot(record, null, null)));
    }

    @Override
    public Mono<Void> complete(String id, String result) {
        return Mono.fromRunnable(() -> records.computeIfPresent(id, (key, old) ->
                new Snapshot(withStatus(old.record(), AsyncToolRecord.COMPLETED), result, null)));
    }

    @Override
    public Mono<Void> fail(String id, String error) {
        return Mono.fromRunnable(() -> records.computeIfPresent(id, (key, old) ->
                new Snapshot(withStatus(old.record(), AsyncToolRecord.FAILED), null, error)));
    }

    @Override
    public Mono<List<AsyncToolRecord>> findStale(String sessionId, Duration ttl) {
        return Mono.fromSupplier(() -> records.values().stream()
                .map(Snapshot::record)
                .filter(r -> sessionId.equals(r.sessionId()))
                .filter(r -> AsyncToolRecord.RUNNING.equals(r.status()))
                .filter(r -> r.createdAt().plus(ttl).isBefore(Instant.now()))
                .toList());
    }

    @Override
    public Mono<Void> markTimeout(String id) {
        return Mono.fromRunnable(() -> records.computeIfPresent(id, (key, old) ->
                new Snapshot(withStatus(old.record(), AsyncToolRecord.TIMEOUT), old.result(), old.error())));
    }

    public List<Snapshot> snapshot() { return List.copyOf(records.values()); }
    public void clear() { records.clear(); }

    private static AsyncToolRecord withStatus(AsyncToolRecord record, String status) {
        return new AsyncToolRecord(record.id(), record.sessionId(), record.toolName(), record.toolCallId(), status, record.createdAt());
    }

    public record Snapshot(AsyncToolRecord record, String result, String error) {}
}
