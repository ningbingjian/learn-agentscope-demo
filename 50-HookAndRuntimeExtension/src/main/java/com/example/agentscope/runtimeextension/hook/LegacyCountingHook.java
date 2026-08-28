package com.example.agentscope.runtimeextension.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;

/** Compatibility-only demonstration. Hook is deprecated for removal in AgentScope Java 2.0. */
@SuppressWarnings("removal")
public final class LegacyCountingHook implements Hook {
    private final AtomicInteger events = new AtomicInteger();
    private final CopyOnWriteArrayList<String> eventTypes = new CopyOnWriteArrayList<>();
    private final int priority;

    public LegacyCountingHook(int priority) { this.priority = priority; }

    @Override
    public int priority() { return priority; }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        events.incrementAndGet();
        eventTypes.add(event.getClass().getSimpleName());
        return Mono.just(event);
    }

    public int count() { return events.get(); }
    public List<String> eventTypes() { return List.copyOf(eventTypes); }
}
