package com.example.agentscope.permissionhitl.web;

import com.example.agentscope.permissionhitl.support.HitlSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final ReActAgent agent;

    public RefundController(ReActAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/start")
    RefundResponse start(@RequestBody RefundStartRequest request) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        requireText(request.orderId(), "orderId");
        if (request.amount() == null || request.amount() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "amount must be greater than 0"
            );
        }

        RuntimeContext context = context(request.userId(), request.sessionId());
        String prompt = "请为订单 " + request.orderId()
                + " 退款 " + request.amount()
                + " 元。必须调用 issue_refund 工具执行。";

        Msg reply = agent.call(List.of(new UserMessage(prompt)), context).block();
        return toResponse(request.userId(), request.sessionId(), reply);
    }

    @PostMapping("/confirm")
    RefundResponse confirm(@RequestBody RefundConfirmRequest request) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        if (request.approved() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "approved must not be null"
            );
        }

        List<ToolUseBlock> askingTools = HitlSupport.findLatestAskingTools(
                agent.getAgentState(request.userId(), request.sessionId()).getContext()
        );
        if (askingTools.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "no pending permission confirmation for this session"
            );
        }

        Msg resumeMessage = HitlSupport.buildResumeMessage(request.approved(), askingTools);
        Msg reply = agent.call(
                        List.of(resumeMessage),
                        context(request.userId(), request.sessionId())
                )
                .block();

        return toResponse(request.userId(), request.sessionId(), reply);
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
    }

    private static RefundResponse toResponse(String userId, String sessionId, Msg reply) {
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }

        List<PendingTool> pendingTools = HitlSupport.extractAskingTools(reply).stream()
                .map(RefundController::toPendingTool)
                .toList();

        String status = reply.getGenerateReason() == GenerateReason.PERMISSION_ASKING
                ? "WAITING_CONFIRMATION"
                : "COMPLETED";

        return new RefundResponse(
                userId,
                sessionId,
                status,
                reply.getGenerateReason().name(),
                reply.getTextContent(),
                pendingTools
        );
    }

    private static PendingTool toPendingTool(ToolUseBlock tool) {
        return new PendingTool(tool.getId(), tool.getName(), tool.getInput());
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must not be blank"
            );
        }
    }

    public record RefundStartRequest(
            String userId,
            String sessionId,
            String orderId,
            Double amount
    ) {
    }

    public record RefundConfirmRequest(String userId, String sessionId, Boolean approved) {
    }

    public record PendingTool(String id, String name, Map<String, Object> input) {
    }

    public record RefundResponse(
            String userId,
            String sessionId,
            String status,
            String generateReason,
            String reply,
            List<PendingTool> pendingTools
    ) {
    }
}
