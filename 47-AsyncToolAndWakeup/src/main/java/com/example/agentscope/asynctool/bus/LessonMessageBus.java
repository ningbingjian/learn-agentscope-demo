package com.example.agentscope.asynctool.bus;

import io.agentscope.harness.agent.bus.BusEntry;
import io.agentscope.harness.agent.bus.MessageBus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LessonMessageBus implements MessageBus {
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Deque<BusEntry>> queues = new ConcurrentHashMap<>();
    private final Map<String, List<BusEntry>> logs = new ConcurrentHashMap<>();

    @Override
    public Mono<String> queuePush(String key, Map<String, Object> payload) {
        return Mono.fromSupplier(() -> {
            String id = Long.toString(sequence.incrementAndGet());
            Deque<BusEntry> queue = queues.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            synchronized (queue) { queue.addLast(new BusEntry(id, Map.copyOf(payload))); }
            return id;
        });
    }

    @Override
    public Mono<List<BusEntry>> queueDrain(String key, int maxCount) {
        return Mono.fromSupplier(() -> {
            Deque<BusEntry> queue = queues.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            List<BusEntry> result = new ArrayList<>();
            synchronized (queue) {
                while (result.size() < maxCount && !queue.isEmpty()) result.add(queue.removeFirst());
            }
            return List.copyOf(result);
        });
    }

    @Override
    public Mono<Void> queueDelete(String key) { return Mono.fromRunnable(() -> queues.remove(key)); }

    @Override
    public Mono<Boolean> queuePeek(String key) {
        return Mono.fromSupplier(() -> {
            Deque<BusEntry> queue = queues.get(key);
            if (queue == null) return false;
            synchronized (queue) { return !queue.isEmpty(); }
        });
    }

    @Override
    public Mono<String> logAppend(String key, Map<String, Object> payload, int maxLen) {
        return Mono.fromSupplier(() -> {
            String id = Long.toString(sequence.incrementAndGet());
            List<BusEntry> log = logs.computeIfAbsent(key, ignored -> new ArrayList<>());
            synchronized (log) {
                log.add(new BusEntry(id, Map.copyOf(payload)));
                while (maxLen > 0 && log.size() > maxLen) log.remove(0);
            }
            return id;
        });
    }

    @Override
    public Mono<List<BusEntry>> logRead(String key, String since, int maxCount) {
        return Mono.fromSupplier(() -> {
            List<BusEntry> log = logs.getOrDefault(key, List.of());
            synchronized (log) {
                int start = 0;
                if (since != null) {
                    for (int i = 0; i < log.size(); i++) if (since.equals(log.get(i).entryId())) start = i + 1;
                }
                return List.copyOf(log.subList(start, Math.min(log.size(), start + maxCount)));
            }
        });
    }

    @Override
    public Mono<Void> logTrim(String key) { return Mono.fromRunnable(() -> logs.remove(key)); }
    @Override
    public Mono<Void> publish(String key, Map<String, Object> payload) { return Mono.empty(); }
    @Override
    public Flux<Map<String, Object>> subscribe(String key) { return Flux.never(); }

    public void clear() { queues.clear(); logs.clear(); }
}
