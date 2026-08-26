package com.example.agentscope.planmode.web;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plan")
public class PlanModeController {

    private final HarnessAgent agent;

    public PlanModeController(HarnessAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        RuntimeContext context = context(request.userId(), request.sessionId());
        Msg reply = agent.call(List.of(new UserMessage(request.message())), context).block();

        Map<String, Object> result = state(request.userId(), request.sessionId());
        result.put("answer", reply != null ? reply.getTextContent() : null);
        result.put("generateReason", reply != null ? reply.getGenerateReason() : null);
        return result;
    }

    @PostMapping("/enter")
    public Map<String, Object> enter(
            @RequestParam(defaultValue = "demo-user") String userId,
            @RequestParam(defaultValue = "demo-session") String sessionId) {
        agent.enterPlanMode(context(userId, sessionId));
        return state(userId, sessionId);
    }

    @PostMapping("/exit")
    public Map<String, Object> exit(
            @RequestParam(defaultValue = "demo-user") String userId,
            @RequestParam(defaultValue = "demo-session") String sessionId) {
        agent.exitPlanMode(context(userId, sessionId));
        return state(userId, sessionId);
    }

    @GetMapping("/state")
    public Map<String, Object> state(
            @RequestParam(defaultValue = "demo-user") String userId,
            @RequestParam(defaultValue = "demo-session") String sessionId) {
        RuntimeContext context = context(userId, sessionId);
        WorkspaceManager workspace = agent.workspaceFor(userId, sessionId);
        String plan = workspace.readManagedWorkspaceFileUtf8(context, "plans/PLAN.md");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("sessionId", sessionId);
        result.put("planModeActive", agent.isPlanModeActive(context));
        result.put("planPath", "plans/PLAN.md");
        result.put("plan", plan);
        result.put("contextSize", agent.getDelegate()
                .getAgentState(userId, sessionId)
                .getContext()
                .size());
        return result;
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
    }

    public record ChatRequest(String userId, String sessionId, String message) {
        public ChatRequest {
            userId = normalize(userId, "demo-user");
            sessionId = normalize(sessionId, "demo-session");
            message = normalize(message,
                    "请先进入 Plan Mode，分析如何给一个 Spring Boot 订单模块增加退款功能，把计划写入 PLAN.md；先不要执行修改。 ");
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
