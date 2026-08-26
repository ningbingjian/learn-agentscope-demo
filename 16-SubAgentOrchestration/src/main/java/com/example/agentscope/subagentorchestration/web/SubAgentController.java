package com.example.agentscope.subagentorchestration.web;

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
@RequestMapping("/api/subagents")
public class SubAgentController {

    private final HarnessAgent agent;

    public SubAgentController(HarnessAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        RuntimeContext context = RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .build();

        Msg reply = agent.call(List.of(new UserMessage(request.message())), context).block();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", request.userId());
        result.put("sessionId", request.sessionId());
        result.put("answer", reply != null ? reply.getTextContent() : null);
        result.put("generateReason", reply != null ? reply.getGenerateReason() : null);
        result.put("tip", "如果任务适合拆分，观察回答中是否出现对子 Agent 的委派与汇总。也可配合第 04 课的 streamEvents 观察 agent_spawn 工具事件。");
        return result;
    }

    @GetMapping("/specs")
    public Map<String, String> specs(
            @RequestParam(defaultValue = "demo-user") String userId,
            @RequestParam(defaultValue = "demo-session") String sessionId) {
        RuntimeContext context = RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
        WorkspaceManager workspace = agent.workspaceFor(userId, sessionId);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("researcher", workspace.readManagedWorkspaceFileUtf8(
                context, "subagents/researcher.md"));
        result.put("reviewer", workspace.readManagedWorkspaceFileUtf8(
                context, "subagents/reviewer.md"));
        return result;
    }

    public record ChatRequest(String userId, String sessionId, String message) {
        public ChatRequest {
            userId = normalize(userId, "demo-user");
            sessionId = normalize(sessionId, "demo-session");
            message = normalize(message, "请让研究员和评审员分别分析 AgentScope 子 Agent 的价值，然后你汇总。 ");
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
