package com.example.agentscope.skills.web;

import com.example.agentscope.skills.service.SkillCatalogService;
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
@RequestMapping("/api/skills")
public class SkillController {

    private final HarnessAgent agent;
    private final SkillCatalogService catalogService;

    public SkillController(HarnessAgent agent, SkillCatalogService catalogService) {
        this.agent = agent;
        this.catalogService = catalogService;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        RuntimeContext context = RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .build();

        Msg reply = agent.call(List.of(new UserMessage(request.message())), context).block();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", reply != null ? reply.getTextContent() : null);
        result.put("generateReason", reply != null ? reply.getGenerateReason() : null);
        result.put("skills", catalogService.listSkills());
        return result;
    }

    @GetMapping("/catalog")
    public List<SkillCatalogService.SkillSummary> catalog() {
        return catalogService.listSkills();
    }

    @GetMapping("/files")
    public Map<String, String> files(
            @RequestParam(defaultValue = "demo-user") String userId,
            @RequestParam(defaultValue = "demo-session") String sessionId) {
        RuntimeContext context = RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
        WorkspaceManager workspace = agent.workspaceFor(userId, sessionId);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("java-code-review/SKILL.md", workspace.readManagedWorkspaceFileUtf8(
                context, "skills/java-code-review/SKILL.md"));
        result.put("java-code-review/references/checklist.md", workspace.readManagedWorkspaceFileUtf8(
                context, "skills/java-code-review/references/checklist.md"));
        result.put("api-design/SKILL.md", workspace.readManagedWorkspaceFileUtf8(
                context, "skills/api-design/SKILL.md"));
        return result;
    }

    public record ChatRequest(String userId, String sessionId, String message) {
        public ChatRequest {
            userId = normalize(userId, "demo-user");
            sessionId = normalize(sessionId, "demo-session");
            message = normalize(message,
                    "请 review 一个 Spring Boot Controller 设计：Controller 里直接写 SQL、没有参数校验、异常直接返回 500。请按项目已有 skill 执行评审。 ");
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
