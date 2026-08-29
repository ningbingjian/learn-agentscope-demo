package com.example.agentscope.applicationrag.web;

import com.example.agentscope.applicationrag.domain.RetrievedDocument;
import com.example.agentscope.applicationrag.service.SimpleKnowledgeRetriever;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final ReActAgent agent;
    private final SimpleKnowledgeRetriever retriever;

    public RagController(ReActAgent agent, SimpleKnowledgeRetriever retriever) {
        this.agent = agent;
        this.retriever = retriever;
    }

    @PostMapping("/ask")
    RagResponse ask(@RequestBody RagRequest request) {
        requireText(request.userId(), "userId");
        requireText(request.sessionId(), "sessionId");
        requireText(request.question(), "question");

        List<RetrievedDocument> retrieved = retriever.retrieve(request.question(), 3);
        String prompt = buildPrompt(request.question(), retrieved);
        RuntimeContext context = RuntimeContext.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .build();
        Msg reply = agent.call(prompt, context).block();
        if (reply == null) {
            throw new IllegalStateException("Agent returned no reply");
        }
        return new RagResponse(
                request.question(),
                reply.getTextContent(),
                retrieved.stream()
                        .map(result -> new Source(
                                result.document().id(),
                                result.document().title(),
                                result.score()
                        ))
                        .toList()
        );
    }

    @GetMapping("/search")
    List<SearchResult> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "3") int limit
    ) {
        requireText(q, "q");
        if (limit < 1 || limit > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 10");
        }
        return retriever.retrieve(q, limit).stream()
                .map(result -> new SearchResult(
                        result.document().id(),
                        result.document().title(),
                        result.document().content(),
                        result.score()
                ))
                .toList();
    }

    private static String buildPrompt(String question, List<RetrievedDocument> documents) {
        StringBuilder knowledge = new StringBuilder();
        if (documents.isEmpty()) {
            knowledge.append("(no relevant documents retrieved)");
        } else {
            IntStream.range(0, documents.size()).forEach(index -> {
                RetrievedDocument result = documents.get(index);
                knowledge.append("[")
                        .append(index + 1)
                        .append("] ")
                        .append(result.document().title())
                        .append("\n")
                        .append(result.document().content())
                        .append("\n\n");
            });
        }
        return """
                请严格根据 <retrieved_knowledge> 中的内容回答用户问题。
                如果这些内容不足以回答，明确说“知识库信息不足”。不要补充检索结果之外的事实。

                <retrieved_knowledge>
                %s
                </retrieved_knowledge>

                用户问题：%s
                """.formatted(knowledge, question);
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must not be blank");
        }
    }

    public record RagRequest(String userId, String sessionId, String question) {
    }

    public record Source(String id, String title, int score) {
    }

    public record RagResponse(String question, String answer, List<Source> sources) {
    }

    public record SearchResult(String id, String title, String content, int score) {
    }
}
