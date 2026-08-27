package com.example.agentscope.enterprisechannels.service;

import io.agentscope.extensions.channel.common.BotLoopGuard;
import io.agentscope.extensions.channel.common.IdempotencyStore;
import io.agentscope.extensions.channel.dingtalk.DingTalkChannel;
import io.agentscope.extensions.channel.feishu.FeishuChannel;
import io.agentscope.extensions.channel.github.GitHubChannel;
import io.agentscope.extensions.channel.gitlab.GitLabChannel;
import io.agentscope.extensions.channel.wecom.WeComChannel;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ChannelCatalogService {

    private final IdempotencyStore idempotencyStore = new IdempotencyStore();
    private final BotLoopGuard botLoopGuard = new BotLoopGuard(5, 60_000L, 10_000L);

    public List<Map<String, Object>> providers() {
        return List.of(
                provider("dingtalk", "agentscope-extensions-channel-dingtalk", DingTalkChannel.class,
                        "Stream / persistent WebSocket"),
                provider("feishu", "agentscope-extensions-channel-feishu", FeishuChannel.class,
                        "HTTP event subscription callback"),
                provider("wecom", "agentscope-extensions-channel-wecom", WeComChannel.class,
                        "Encrypted HTTP callback"),
                provider("github", "agentscope-extensions-channel-github", GitHubChannel.class,
                        "GitHub webhook"),
                provider("gitlab", "agentscope-extensions-channel-gitlab", GitLabChannel.class,
                        "GitLab webhook")
        );
    }

    public Map<String, Object> inspectInboundGuard(String peerKey, String messageId) {
        boolean firstSeen = idempotencyStore.firstSeen(messageId);
        boolean withinLoopBudget = firstSeen && botLoopGuard.allow(peerKey);
        return Map.of(
                "peerKey", peerKey,
                "messageId", messageId,
                "firstSeen", firstSeen,
                "withinLoopBudget", withinLoopBudget,
                "coolingDown", botLoopGuard.isCoolingDown(peerKey),
                "dedupSize", idempotencyStore.size()
        );
    }

    private Map<String, Object> provider(
            String name, String artifact, Class<?> type, String transport) {
        return Map.of(
                "name", name,
                "artifact", artifact,
                "class", type.getName(),
                "transport", transport
        );
    }
}
