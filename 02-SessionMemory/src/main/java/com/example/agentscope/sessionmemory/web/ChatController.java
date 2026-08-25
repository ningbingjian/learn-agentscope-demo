package com.example.agentscope.sessionmemory.web;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final HarnessAgent agent;

    public ChatController(HarnessAgent agent) {
        this.agent = agent;
    }

    @PostMapping
    ChatResponse chat(@RequestBody ChatRequest request) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        requireText(request.message(), "message");

        RuntimeContext context = RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .build();

        Msg reply = agent.call(new UserMessage(request.message()), context).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }

        return new ChatResponse(reply.getTextContent());
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must not be blank"
            );
        }
    }

    public record ChatRequest(String userId, String sessionId, String message) {
    }

    public record ChatResponse(String reply) {
    }
}
