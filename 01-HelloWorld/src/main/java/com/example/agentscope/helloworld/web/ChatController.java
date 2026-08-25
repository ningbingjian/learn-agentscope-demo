package com.example.agentscope.helloworld.web;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
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

    private final ReActAgent agent;

    public ChatController(ReActAgent agent) {
        this.agent = agent;
    }

    @PostMapping
    ChatResponse chat(@RequestBody ChatRequest request) {
        if (!StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "message must not be blank"
            );
        }

        Msg reply = agent.call(request.message(), RuntimeContext.empty()).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }

        return new ChatResponse(reply.getTextContent());
    }

    public record ChatRequest(String message) {
    }

    public record ChatResponse(String reply) {
    }
}
