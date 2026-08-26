package com.example.agentscope.gatewayandchannel.web;

import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
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
@RequestMapping("/api/channel")
public class ChannelController {

    private final ChatUiChannel chat;

    public ChannelController(ChatUiChannel chat) {
        this.chat = chat;
    }

    @PostMapping("/chat")
    ChatResponse chat(@RequestBody ChatRequest request) {
        validate(request.userId(), request.message());
        SendOptions options = options(request.userId(), request.sessionId());
        Msg reply = chat.send(options, request.message()).block();
        return new ChatResponse(
                request.userId(),
                request.sessionId(),
                reply == null ? "" : reply.getTextContent()
        );
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<Map<String, Object>>> stream(@RequestBody ChatRequest request) {
        validate(request.userId(), request.message());
        return chat.sendStream(options(request.userId(), request.sessionId()), request.message())
                .filter(GatewayEventSseMapper::supports)
                .map(GatewayEventSseMapper::toSse);
    }

    @PostMapping("/subagent/chat")
    SubagentChatResponse subagentChat(@RequestBody SubagentRequest request) {
        validateSubagent(request);
        Msg reply = chat.sendToSubagent(request.subagentId(), request.message()).block();
        return new SubagentChatResponse(
                request.subagentId(),
                reply == null ? "" : reply.getTextContent()
        );
    }

    @PostMapping(path = "/subagent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<Map<String, Object>>> subagentStream(@RequestBody SubagentRequest request) {
        validateSubagent(request);
        return chat.sendToSubagentStream(request.subagentId(), request.message())
                .filter(GatewayEventSseMapper::supports)
                .map(GatewayEventSseMapper::toSse);
    }

    private static SendOptions options(String userId, String sessionId) {
        return StringUtils.hasText(sessionId)
                ? SendOptions.of(userId, sessionId)
                : SendOptions.userId(userId);
    }

    private static void validate(String userId, String message) {
        if (!StringUtils.hasText(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must not be blank");
        }
        if (!StringUtils.hasText(message)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
    }

    private static void validateSubagent(SubagentRequest request) {
        if (!StringUtils.hasText(request.subagentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subagentId must not be blank");
        }
        if (!StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
    }

    public record ChatRequest(String userId, String sessionId, String message) {
    }

    public record ChatResponse(String userId, String sessionId, String reply) {
    }

    public record SubagentRequest(String subagentId, String message) {
    }

    public record SubagentChatResponse(String subagentId, String reply) {
    }
}
