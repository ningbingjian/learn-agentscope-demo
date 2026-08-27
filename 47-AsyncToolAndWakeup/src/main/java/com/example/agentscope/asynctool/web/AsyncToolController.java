package com.example.agentscope.asynctool.web;

import com.example.agentscope.asynctool.bus.LessonAsyncToolRegistry;
import com.example.agentscope.asynctool.bus.LessonMessageBus;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.bus.BusEntry;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/async")
public class AsyncToolController {
    private final HarnessAgent agent;
    private final LessonMessageBus bus;
    private final LessonAsyncToolRegistry registry;

    public AsyncToolController(HarnessAgent agent, LessonMessageBus bus, LessonAsyncToolRegistry registry) {
        this.agent = agent; this.bus = bus; this.registry = registry;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        RuntimeContext ctx = RuntimeContext.builder().userId(request.userId()).sessionId(request.sessionId()).build();
        Msg reply = agent.call(new UserMessage(request.message()), ctx).block();
        return Map.of("reply", reply == null ? "" : reply.getTextContent(), "sessionId", request.sessionId());
    }

    @GetMapping("/inbox/{sessionId}")
    public List<BusEntry> inbox(@PathVariable String sessionId) {
        return bus.inboxDrain(sessionId, 100).blockOptional().orElse(List.of());
    }

    @GetMapping("/wakeups")
    public List<BusEntry> wakeups() {
        return bus.queueDrain("agentscope:wakeups", 100).blockOptional().orElse(List.of());
    }

    @GetMapping("/registry")
    public List<LessonAsyncToolRegistry.Snapshot> registry() { return registry.snapshot(); }

    public record ChatRequest(String userId, String sessionId, String message) {}
}
