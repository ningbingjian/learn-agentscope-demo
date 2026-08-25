package com.example.agentscope.structuredoutput.domain;

public record TicketAnalysis(
        String category,
        String priority,
        String summary,
        boolean needHuman
) {
}
