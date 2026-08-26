package com.example.agentscope.adminops.web;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin-demo")
public class AdminDemoController {

    private final ReActAgent agent;

    public AdminDemoController(ReActAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        RuntimeContext context = RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .build();
        Msg reply = agent.call(new UserMessage(request.message()), context).block();
        return Map.of(
                "reply", reply == null ? "" : reply.getTextContent(),
                "agent", agent.getName(),
                "sessionId", request.sessionId());
    }

    @GetMapping("/guide")
    public Map<String, Object> guide() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("readOnlyActuator", List.of(
                "/actuator/agentscope-status",
                "/actuator/agentscope-agents",
                "/actuator/agentscope-tools",
                "/actuator/agentscope-models",
                "/actuator/agentscope-usage",
                "/actuator/agentscope-commands",
                "/actuator/agentscope-permissions",
                "/actuator/agentscope-subagents",
                "/actuator/agentscope-doctor"));
        out.put("dangerousControl", List.of(
                "/actuator/agentscope-drain",
                "/actuator/agentscope-shutdown"));
        out.put("dataPlane", List.of(
                "GET /v1/admin/sessions",
                "GET /v1/admin/sessions/{sessionId}/messages",
                "GET /v1/admin/sessions/{sessionId}/state",
                "GET /v1/admin/sessions/{sessionId}:export",
                "POST /v1/admin/sessions/{sessionId}:compact",
                "POST /v1/admin/sessions/{sessionId}:abort",
                "POST /v1/admin/sessions/{sessionId}:undo",
                "POST /v1/admin/sessions/{sessionId}:redo"));
        out.put("writeEnabledByDefault", false);
        out.put("writeTokenHeader", "X-Agentscope-Admin-Token");
        return out;
    }

    public record ChatRequest(String userId, String sessionId, String message) {}
}
