package com.example.agentscope.applicationrag.domain;

public record RetrievedDocument(
        KnowledgeDocument document,
        int score
) {
}
