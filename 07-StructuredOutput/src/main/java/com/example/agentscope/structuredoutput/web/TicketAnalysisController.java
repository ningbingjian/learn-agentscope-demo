package com.example.agentscope.structuredoutput.web;

import com.example.agentscope.structuredoutput.domain.TicketAnalysis;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketAnalysisController {

    private final ReActAgent agent;

    public TicketAnalysisController(ReActAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/analyze")
    TicketAnalysisResponse analyze(@RequestBody TicketAnalysisRequest request) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        requireText(request.message(), "message");

        RuntimeContext context = RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .build();

        Msg reply = agent.call(
                        List.of(new UserMessage(request.message())),
                        TicketAnalysis.class,
                        context
                )
                .block();

        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }
        if (!reply.hasStructuredData()) {
            throw new IllegalStateException("Agent reply does not contain structured data");
        }

        TicketAnalysis analysis = reply.getStructuredData(TicketAnalysis.class);
        return new TicketAnalysisResponse(
                request.userId(),
                request.sessionId(),
                reply.getGenerateReason().name(),
                analysis
        );
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must not be blank"
            );
        }
    }

    public record TicketAnalysisRequest(String userId, String sessionId, String message) {
    }

    public record TicketAnalysisResponse(
            String userId,
            String sessionId,
            String generateReason,
            TicketAnalysis analysis
    ) {
    }
}
