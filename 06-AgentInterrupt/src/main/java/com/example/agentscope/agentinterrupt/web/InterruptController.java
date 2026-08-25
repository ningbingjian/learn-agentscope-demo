package com.example.agentscope.agentinterrupt.web;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/interrupt")
public class InterruptController {

    private static final Logger log = LoggerFactory.getLogger(InterruptController.class);

    private final HarnessAgent agent;

    public InterruptController(HarnessAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/run")
    RunResponse run(@RequestBody RunRequest request) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        requireDelay(request.delaySeconds());

        String requestId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();

        log.info(
                "interrupt demo started: requestId={}, userId={}, sessionId={}, delaySeconds={}",
                requestId,
                request.userId(),
                request.sessionId(),
                request.delaySeconds()
        );

        RuntimeContext context = RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .build();

        String prompt = "请调用 pause 工具等待 " + request.delaySeconds()
                + " 秒。工具完成后只回答：任务完成。";
        Msg reply = agent.call(new UserMessage(prompt), context).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }

        Instant completedAt = Instant.now();
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;

        log.info(
                "interrupt demo finished: requestId={}, userId={}, sessionId={}, reason={}, elapsedMillis={}",
                requestId,
                request.userId(),
                request.sessionId(),
                reply.getGenerateReason(),
                elapsedMillis
        );

        return new RunResponse(
                requestId,
                request.userId(),
                request.sessionId(),
                reply.getTextContent(),
                reply.getGenerateReason().name(),
                startedAt,
                completedAt,
                elapsedMillis
        );
    }

    @PostMapping("/cancel")
    InterruptResponse interrupt(@RequestBody InterruptRequest request) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");

        RuntimeContext target = RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .build();

        // AgentScope Java 2.0.1 的 HarnessAgent 尚未直接暴露 per-session interrupt 重载。
        // 通过 delegate 调用 ReActAgent 的 RuntimeContext 版本，精准中断目标会话。
        agent.getDelegate().interrupt(target);

        Instant signaledAt = Instant.now();
        log.info(
                "interrupt signal sent: userId={}, sessionId={}",
                request.userId(),
                request.sessionId()
        );

        return new InterruptResponse(
                request.userId(),
                request.sessionId(),
                "INTERRUPT_SIGNAL_SENT",
                signaledAt
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

    private static void requireDelay(Integer delaySeconds) {
        if (delaySeconds == null || delaySeconds < 1 || delaySeconds > 30) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "delaySeconds must be between 1 and 30"
            );
        }
    }

    public record RunRequest(String userId, String sessionId, Integer delaySeconds) {
    }

    public record InterruptRequest(String userId, String sessionId) {
    }

    public record RunResponse(
            String requestId,
            String userId,
            String sessionId,
            String reply,
            String generateReason,
            Instant startedAt,
            Instant completedAt,
            long elapsedMillis
    ) {
    }

    public record InterruptResponse(
            String userId,
            String sessionId,
            String status,
            Instant signaledAt
    ) {
    }
}
