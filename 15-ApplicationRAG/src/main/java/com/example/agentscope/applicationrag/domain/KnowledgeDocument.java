package com.example.agentscope.applicationrag.domain;

import java.util.List;

public record KnowledgeDocument(
        String id,
        String title,
        String content,
        List<String> keywords
) {
}
