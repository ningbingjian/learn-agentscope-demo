package com.example.agentscope.streamingevents.web;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class StreamingChatController {

    private final ReActAgent agent;

    public StreamingChatController(ReActAgent agent) {
        this.agent = agent;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<Map<String, Object>>> stream(@RequestBody ChatRequest request) {
        if (!StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "message must not be blank"
            );
        }

        return agent.streamEvents(request.message(), RuntimeContext.empty())
//                .filter(AgentEventSseMapper::supports)
                .map(AgentEventSseMapper::toSse);
    }

    public record ChatRequest(String message) {
    }
}
