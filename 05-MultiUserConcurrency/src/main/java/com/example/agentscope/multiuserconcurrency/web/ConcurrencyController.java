package com.example.agentscope.multiuserconcurrency.web;

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
@RequestMapping("/api/concurrency")
public class ConcurrencyController {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyController.class);

    private final HarnessAgent agent;

    public ConcurrencyController(HarnessAgent agent) {
        this.agent = agent;
    }

    @PostMapping
    ConcurrencyResponse run(@RequestBody ConcurrencyRequest request) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        requireDelay(request.delaySeconds());

        String requestId = UUID.randomUUID().toString();
        Instant acceptedAt = Instant.now();
        long startedNanos = System.nanoTime();
        log.info(
                "request accepted: requestId={}, userId={}, sessionId={}, delaySeconds={}",
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
                + " 秒，工具完成后只回答：等待完成。";
        Msg reply = agent.call(new UserMessage(prompt), context).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }

        Instant completedAt = Instant.now();
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;
        log.info(
                "request completed: requestId={}, userId={}, sessionId={}, elapsedMillis={}",
                requestId,
                request.userId(),
                request.sessionId(),
                elapsedMillis
        );

        return new ConcurrencyResponse(
                requestId,
                request.userId(),
                request.sessionId(),
                reply.getTextContent(),
                acceptedAt,
                completedAt,
                elapsedMillis
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
        if (delaySeconds == null || delaySeconds < 1 || delaySeconds > 10) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "delaySeconds must be between 1 and 10"
            );
        }
    }

    public record ConcurrencyRequest(String userId, String sessionId, Integer delaySeconds) {
    }

    public record ConcurrencyResponse(
            String requestId,
            String userId,
            String sessionId,
            String reply,
            Instant acceptedAt,
            Instant completedAt,
            long elapsedMillis
    ) {
    }
}
