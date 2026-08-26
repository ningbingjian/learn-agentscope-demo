package com.example.agentscope.applicationrag.service;

import com.example.agentscope.applicationrag.domain.KnowledgeDocument;
import com.example.agentscope.applicationrag.domain.RetrievedDocument;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class SimpleKnowledgeRetriever {

    private final List<KnowledgeDocument> documents = List.of(
            new KnowledgeDocument(
                    "runtime-context",
                    "RuntimeContext 与会话状态",
                    "RuntimeContext 是一次调用的上下文，常用 userId 与 sessionId 定位会话。"
                            + "ReActAgent 根据这两个值选择对应的 AgentState；同一 userId + sessionId "
                            + "恢复同一会话，不同组合彼此隔离。",
                    List.of("runtimecontext", "userid", "sessionid", "agentstate", "会话", "状态")
            ),
            new KnowledgeDocument(
                    "tool-calling",
                    "Tool Calling",
                    "Toolkit 用于注册 Java Tool。模型决定调用工具后，AgentScope 执行 Tool，"
                            + "把工具结果放回 ReAct 循环，再由模型生成最终回复。",
                    List.of("tool", "toolkit", "工具", "调用", "react")
            ),
            new KnowledgeDocument(
                    "permission-hitl",
                    "Permission 与 HITL",
                    "Permission System 对工具调用给出 ALLOW、DENY、ASK。命中 ASK 时 Agent 暂停，"
                            + "返回 PERMISSION_ASKING；应用收集用户决定后通过 ConfirmResult 恢复。",
                    List.of("permission", "hitl", "allow", "deny", "ask", "confirmresult", "人工确认")
            ),
            new KnowledgeDocument(
                    "harness-memory",
                    "Harness Memory",
                    "Harness 长期记忆分为每日 memory/YYYY-MM-DD.md 与整理后的 MEMORY.md。"
                            + "Flush 提炼每日事实，Consolidation 周期性合并到 MEMORY.md。",
                    List.of("memory", "flush", "consolidation", "长期记忆", "memory.md")
            ),
            new KnowledgeDocument(
                    "compaction",
                    "Context Compaction",
                    "Compaction 在上下文达到消息数或 token 阈值时，把旧消息前缀总结成一条 summary，"
                            + "并保留最近 tail，从而控制当前 session 的模型上下文长度。",
                    List.of("compaction", "summary", "context", "压缩", "上下文", "token")
            )
    );

    public List<RetrievedDocument> retrieve(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return documents.stream()
                .map(document -> new RetrievedDocument(document, score(normalizedQuery, document)))
                .filter(result -> result.score() > 0)
                .sorted(Comparator
                        .comparingInt(RetrievedDocument::score)
                        .reversed()
                        .thenComparing(result -> result.document().id()))
                .limit(limit)
                .toList();
    }

    public List<KnowledgeDocument> allDocuments() {
        return documents;
    }

    private static int score(String query, KnowledgeDocument document) {
        int score = 0;
        for (String keyword : document.keywords()) {
            if (query.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 3;
            }
        }
        String title = document.title().toLowerCase(Locale.ROOT);
        if (query.contains(title)) {
            score += 5;
        }
        for (String token : query.split("[\\s,，。！？?]+")) {
            if (token.length() >= 2 && document.content().toLowerCase(Locale.ROOT).contains(token)) {
                score += 1;
            }
        }
        return score;
    }
}
