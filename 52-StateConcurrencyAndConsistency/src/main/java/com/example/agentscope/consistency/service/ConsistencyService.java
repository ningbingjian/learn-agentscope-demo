package com.example.agentscope.consistency.service;

import com.example.agentscope.consistency.model.ConcurrentProbeModel;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.mysql.store.JdbcStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ConsistencyService {
    private static final List<String> SESSION_NS = List.of("lesson52", "session");
    private static final List<String> IDEMPOTENCY_NS = List.of("lesson52", "idempotency");

    private final ReActAgent agent;
    private final ConcurrentProbeModel model;
    private final JdbcStore store;

    public ConsistencyService(ReActAgent agent, ConcurrentProbeModel model, JdbcStore store) {
        this.agent = agent;
        this.model = model;
        this.store = store;
    }

    public Map<String, Object> serializationDemo() {
        model.reset();
        Mono.zip(
                agent.call(new UserMessage("same-1"), context("u1", "same-session")),
                agent.call(new UserMessage("same-2"), context("u1", "same-session")))
                .block();
        int sameSessionMax = model.maxActive();

        model.reset();
        Mono.zip(
                agent.call(new UserMessage("different-1"), context("u1", "session-a")),
                agent.call(new UserMessage("different-2"), context("u1", "session-b")))
                .block();
        int differentSessionMax = model.maxActive();

        return Map.of(
                "sameSessionMaxConcurrentModelCalls", sameSessionMax,
                "differentSessionMaxConcurrentModelCalls", differentSessionMax,
                "meaning", "same session is serialized inside one Agent instance; different sessions can overlap");
    }

    public Map<String, Object> casRaceDemo() {
        String key = "session-" + UUID.randomUUID();
        boolean created = store.putIfVersion(SESSION_NS, key, Map.of("value", "initial"), 0L);
        StoreItem first = store.get(SESSION_NS, key);
        long expectedVersion = first.version();

        Mono<Boolean> podA = Mono.fromCallable(() -> store.putIfVersion(
                        SESSION_NS, key, Map.of("value", "pod-a"), expectedVersion))
                .subscribeOn(Schedulers.boundedElastic());
        Mono<Boolean> podB = Mono.fromCallable(() -> store.putIfVersion(
                        SESSION_NS, key, Map.of("value", "pod-b"), expectedVersion))
                .subscribeOn(Schedulers.boundedElastic());
        var result = Mono.zip(podA, podB).block();
        StoreItem latest = store.get(SESSION_NS, key);

        return Map.of(
                "created", created,
                "expectedVersion", expectedVersion,
                "podAUpdated", result.getT1(),
                "podBUpdated", result.getT2(),
                "successfulWriters", (result.getT1() ? 1 : 0) + (result.getT2() ? 1 : 0),
                "latestVersion", latest.version(),
                "latestValue", latest.value().get("value"));
    }

    public Map<String, Object> idempotencyDemo() {
        String eventId = "wakeup-" + UUID.randomUUID();
        boolean firstConsumer = store.putIfVersion(
                IDEMPOTENCY_NS, eventId, Map.of("consumer", "pod-a", "status", "processed"), 0L);
        boolean duplicateConsumer = store.putIfVersion(
                IDEMPOTENCY_NS, eventId, Map.of("consumer", "pod-b", "status", "processed"), 0L);
        return Map.of(
                "eventId", eventId,
                "firstAccepted", firstConsumer,
                "duplicateAccepted", duplicateConsumer,
                "processedExactlyOnce", firstConsumer && !duplicateConsumer);
    }

    public Map<String, Object> architecture() {
        return Map.of(
                "singleJvm", "ReActAgent serializes equal (userId, sessionId) calls",
                "crossJvm", "use shared CAS/lock/idempotency because local serialization does not cross Pod boundaries",
                "cas", "JdbcStore.putIfVersion is atomic across JVMs",
                "duplicateEvents", "claim an idempotency key before applying side effects",
                "warning", "multi-record atomicity still needs a database transaction/outbox; CAS on one key is not a distributed transaction");
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
    }
}
