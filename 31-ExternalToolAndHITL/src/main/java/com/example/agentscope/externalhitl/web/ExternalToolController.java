package com.example.agentscope.externalhitl.web;

import com.example.agentscope.externalhitl.tool.ExternalNotificationTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ExternalExecutionResultEvent;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/external")
public class ExternalToolController {

    private final ReActAgent agent;
    private final ExternalNotificationTools tools;

    public ExternalToolController(ReActAgent agent, ExternalNotificationTools tools) {
        this.agent = agent;
        this.tools = tools;
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestBody StartRequest request) {
        RuntimeContext context = context(request.userId(), request.sessionId());
        Msg reply = agent.call(new UserMessage(request.message()), context).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }
        ToolUseBlock pending = reply.getFirstContentBlock(ToolUseBlock.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generateReason", String.valueOf(reply.getGenerateReason()));
        out.put("suspended", reply.getGenerateReason() == GenerateReason.TOOL_SUSPENDED);
        out.put("toolBodyExecutions", tools.bodyExecutions());
        if (pending != null) {
            out.put("toolCallId", pending.getId());
            out.put("toolName", pending.getName());
            out.put("toolInput", pending.getInput());
        }
        return out;
    }

    @PostMapping("/complete")
    public Map<String, Object> complete(@RequestBody CompleteRequest request) {
        RuntimeContext context = context(request.userId(), request.sessionId());
        ToolUseBlock pending = findToolCall(context, request.toolCallId());
        ToolResultBlock result = ToolResultBlock.builder()
                .id(pending.getId())
                .name(pending.getName())
                .output(List.of(TextBlock.builder().text(request.output()).build()))
                .state(request.success() ? ToolResultState.SUCCESS : ToolResultState.ERROR)
                .build();
        Msg toolMessage = Msg.builder()
                .name("external-system")
                .role(MsgRole.TOOL)
                .content(result)
                .build();
        Msg finalReply = agent.call(List.of(toolMessage), context).block();
        if (finalReply == null) {
            throw new IllegalStateException("Agent returned no final reply");
        }
        return Map.of(
                "reply", finalReply.getTextContent(),
                "generateReason", String.valueOf(finalReply.getGenerateReason()),
                "toolBodyExecutions", tools.bodyExecutions());
    }

    @GetMapping("/event-contract")
    public Map<String, Object> eventContract() {
        ToolUseBlock call = ToolUseBlock.builder()
                .id("sample-call")
                .name("external_send_notification")
                .input(Map.of("channel", "ops-demo", "message", "hello"))
                .build();
        ToolResultBlock result = ToolResultBlock.builder()
                .id(call.getId())
                .name(call.getName())
                .output(TextBlock.builder().text("accepted by operator").build())
                .state(ToolResultState.SUCCESS)
                .build();
        RequireExternalExecutionEvent required =
                new RequireExternalExecutionEvent("sample-reply", List.of(call));
        ExternalExecutionResultEvent completed =
                new ExternalExecutionResultEvent("sample-reply", List.of(result));
        return Map.of(
                "requireEventType", required.getType().name(),
                "resultEventType", completed.getType().name(),
                "replyId", required.getReplyId(),
                "toolCallId", required.getToolCalls().get(0).getId());
    }

    private ToolUseBlock findToolCall(RuntimeContext context, String toolCallId) {
        AgentState state = agent.getAgentState(context);
        if (state == null) {
            throw new IllegalStateException("No AgentState for this session");
        }
        List<Msg> messages = state.getContext();
        for (int i = messages.size() - 1; i >= 0; i--) {
            for (ToolUseBlock call : messages.get(i).getContentBlocks(ToolUseBlock.class)) {
                if (toolCallId.equals(call.getId())) {
                    return call;
                }
            }
        }
        throw new IllegalArgumentException("Unknown toolCallId: " + toolCallId);
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
    }

    public record StartRequest(String userId, String sessionId, String message) {}

    public record CompleteRequest(
            String userId,
            String sessionId,
            String toolCallId,
            String output,
            boolean success) {}
}
