package com.example.agentscope.ragintegrations.rag;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * A tiny deterministic embedding model used only for learning/tests.
 * It makes the RAG pipeline runnable without a remote embedding service.
 */
public class KeywordEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 4;

    @Override
    public Mono<double[]> embed(ContentBlock block) {
        if (!(block instanceof TextBlock textBlock)) {
            return Mono.error(new IllegalArgumentException("Only TextBlock is supported"));
        }
        String text = textBlock.getText().toLowerCase(Locale.ROOT);
        double[] v = new double[] {
                score(text, "agentscope", "agent", "智能体"),
                score(text, "redis", "distributed", "分布式"),
                score(text, "sandbox", "docker", "沙箱"),
                score(text, "skill", "技能", "skill.md")
        };
        double norm = 0.0;
        for (double x : v) {
            norm += x * x;
        }
        if (norm == 0.0) {
            v[0] = 0.25;
            v[1] = 0.25;
            v[2] = 0.25;
            v[3] = 0.25;
            norm = 0.25;
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] /= norm;
        }
        return Mono.just(v);
    }

    private double score(String text, String... keywords) {
        double score = 0.0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                score += 1.0;
            }
        }
        return score;
    }

    @Override
    public String getModelName() {
        return "lesson-keyword-embedding";
    }

    @Override
    public int getDimensions() {
        return DIMENSIONS;
    }
}
