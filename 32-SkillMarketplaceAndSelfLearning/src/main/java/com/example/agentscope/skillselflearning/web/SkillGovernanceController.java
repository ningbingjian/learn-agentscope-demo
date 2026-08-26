package com.example.agentscope.skillselflearning.web;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
public class SkillGovernanceController {

    private final HarnessAgent agent;

    public SkillGovernanceController(HarnessAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/propose")
    public Map<String, Object> propose(@RequestBody ProposeRequest request) {
        RuntimeContext context = context(request.userId(), request.sessionId());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", request.name());
        input.put("description", request.description());
        input.put("body", request.body());
        if (request.scripts() != null && !request.scripts().isEmpty()) {
            input.put("scripts", request.scripts());
        }
        ToolUseBlock use = ToolUseBlock.builder()
                .id("propose-" + UUID.randomUUID())
                .name("propose_skill")
                .input(input)
                .build();
        ToolResultBlock result = agent.getToolkit().callTool(ToolCallParam.builder()
                .toolUseBlock(use)
                .input(input)
                .agent(agent)
                .runtimeContext(context)
                .build()).block();
        return Map.of(
                "tool", "propose_skill",
                "result", text(result),
                "draftPath", "skills/_drafts/" + request.name() + "/SKILL.md");
    }

    @PostMapping("/promote")
    public Object promote(@RequestBody PromoteRequest request) {
        RuntimeContext context = context(request.userId(), request.sessionId());
        return agent.promoteSkill(request.name(), request.reviewerId(), context).block();
    }

    @PostMapping("/curate")
    public Object curate() {
        return agent.runCuratorOnce().block();
    }

    @GetMapping("/audit")
    public Object audit(@RequestParam(required = false) String dayUtc) {
        return agent.queryAudit(dayUtc, entry -> true);
    }

    @GetMapping("/inspect")
    public Map<String, Object> inspect(
            @RequestParam(defaultValue = "alice") String userId,
            @RequestParam(defaultValue = "skill-lesson") String sessionId) {
        WorkspaceManager workspace = agent.workspaceFor(userId, sessionId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("workspace", workspace == null ? null : workspace.getWorkspace().toString());
        out.put("toolNames", agent.getToolkit().getToolNames());
        out.put("selfLearningTools", List.of("propose_skill", "skill_manage"));
        out.put("lifecycle", List.of("draft", "review", "promote", "use", "stale", "archive"));
        out.put("checkedAt", Instant.now().toString());
        return out;
    }

    private static String text(ToolResultBlock block) {
        if (block == null) {
            return "<null>";
        }
        return block.getOutput().stream()
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::getText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
    }

    public record ScriptInput(String path, String content) {}

    public record ProposeRequest(
            String userId,
            String sessionId,
            String name,
            String description,
            String body,
            List<Map<String, Object>> scripts) {}

    public record PromoteRequest(
            String userId,
            String sessionId,
            String name,
            String reviewerId) {}
}
