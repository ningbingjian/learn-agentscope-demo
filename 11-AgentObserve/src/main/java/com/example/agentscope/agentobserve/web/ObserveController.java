package com.example.agentscope.agentobserve.web;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/observe")
public class ObserveController {

    private static final String WRITER_SESSION = "writer-default";

    private final ReActAgent researcherAgent;
    private final ReActAgent writerAgent;

    public ObserveController(
            @Qualifier("researcherAgent") ReActAgent researcherAgent,
            @Qualifier("writerAgent") ReActAgent writerAgent
    ) {
        this.researcherAgent = researcherAgent;
        this.writerAgent = writerAgent;
    }

    @PostMapping("/research")
    ResearchResponse research(@RequestBody ResearchRequest request) {
        requireText(request.topic(), "topic");

        Msg researchReply = researcherAgent
                .call(new UserMessage("请针对下面主题给出 3 到 5 条研究笔记：" + request.topic()))
                .block();
        if (researchReply == null) {
            throw new IllegalStateException("Researcher returned no reply");
        }

        writerAgent.observe(researchReply).block();

        return new ResearchResponse(
                researchReply.getTextContent(),
                writerAgent.getAgentState(null, WRITER_SESSION).getContext().size()
        );
    }

    @PostMapping("/write")
    WriteResponse write(@RequestBody WriteRequest request) {
        requireText(request.instruction(), "instruction");

        Msg reply = writerAgent.call(new UserMessage(request.instruction())).block();
        if (reply == null) {
            throw new IllegalStateException("Writer returned no reply");
        }

        return new WriteResponse(
                reply.getTextContent(),
                writerAgent.getAgentState(null, WRITER_SESSION).getContext().size()
        );
    }

    @GetMapping("/writer-context")
    List<ContextMessage> writerContext() {
        return writerAgent.getAgentState(null, WRITER_SESSION)
                .getContext()
                .stream()
                .map(msg -> new ContextMessage(
                        msg.getRole().name(),
                        msg.getName(),
                        msg.getTextContent()
                ))
                .toList();
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must not be blank"
            );
        }
    }

    public record ResearchRequest(String topic) {
    }

    public record ResearchResponse(String research, int writerContextMessageCount) {
    }

    public record WriteRequest(String instruction) {
    }

    public record WriteResponse(String article, int writerContextMessageCount) {
    }

    public record ContextMessage(String role, String name, String text) {
    }
}
