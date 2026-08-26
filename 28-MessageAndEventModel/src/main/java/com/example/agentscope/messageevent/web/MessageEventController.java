package com.example.agentscope.messageevent.web;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message-event")
public class MessageEventController {

    private final ReActAgent agent;

    public MessageEventController(ReActAgent agent) {
        this.agent = agent;
    }

    @GetMapping("/sample")
    SampleResponse sample() {
        Msg user = new UserMessage("user", "请计算 20 + 22");

        ToolUseBlock toolUse = ToolUseBlock.builder()
                .id("call-1")
                .name("add_numbers")
                .input(Map.of("left", 20, "right", 22))
                .state(ToolCallState.PENDING)
                .build();

        ToolResultBlock toolResult = ToolResultBlock.builder()
                .id("call-1")
                .name("add_numbers")
                .output(TextBlock.builder().text("42").build())
                .state(ToolResultState.SUCCESS)
                .build();

        Msg assistant = Msg.builder()
                .name("message-event-agent")
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("我会先调用工具，再给出最终结果。").build())
                .content(toolUse)
                .content(toolResult)
                .content(TextBlock.builder().text("20 + 22 = 42").build())
                .build();

        return new SampleResponse(MessageView.from(user), MessageView.from(assistant));
    }

    @PostMapping("/chat")
    MessageView chat(@RequestBody ChatRequest request) {
        validate(request);
        Msg reply = agent.call(
                new UserMessage(request.message()),
                context(request.userId(), request.sessionId())
        ).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }
        return MessageView.from(reply);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<AgentEvent> stream(@RequestBody ChatRequest request) {
        validate(request);
        return agent.streamEvents(
                new UserMessage(request.message()),
                context(request.userId(), request.sessionId())
        );
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
    }

    private static void validate(ChatRequest request) {
        if (request == null
                || !StringUtils.hasText(request.userId())
                || !StringUtils.hasText(request.sessionId())
                || !StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "userId, sessionId and message must not be blank"
            );
        }
    }

    public record ChatRequest(String userId, String sessionId, String message) {
    }

    public record SampleResponse(MessageView user, MessageView assistant) {
    }

    public record MessageView(
            String id,
            String name,
            String role,
            String text,
            List<String> blockTypes,
            String generateReason
    ) {
        static MessageView from(Msg msg) {
            List<String> blockTypes = msg.getContent().stream()
                    .map(ContentBlock::getClass)
                    .map(Class::getSimpleName)
                    .toList();
            return new MessageView(
                    msg.getId(),
                    msg.getName(),
                    msg.getRole().name(),
                    msg.getTextContent(),
                    blockTypes,
                    msg.getGenerateReason() == null ? null : msg.getGenerateReason().name()
            );
        }
    }
}
