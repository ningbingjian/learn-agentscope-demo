package com.example.agentscope.enterprisechannels;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.enterprisechannels.service.ChannelCatalogService;
import io.agentscope.extensions.channel.common.BotLoopGuard;
import io.agentscope.extensions.channel.common.IdempotencyStore;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnterpriseChannelsContractTest {

    @Test
    void allFiveOfficialChannelAdaptersAreOnClasspath() {
        ChannelCatalogService service = new ChannelCatalogService();

        assertThat(service.providers())
                .extracting(item -> item.get("name"))
                .containsExactly("dingtalk", "feishu", "wecom", "github", "gitlab");
    }

    @Test
    void inboundWebhookIdsAreDeduplicated() {
        IdempotencyStore store = new IdempotencyStore(60_000L, 100);

        assertThat(store.firstSeen("msg-001")).isTrue();
        assertThat(store.firstSeen("msg-001")).isFalse();
        assertThat(store.firstSeen("msg-002")).isTrue();
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void botLoopGuardTripsAfterPeerExceedsSlidingWindowBudget() {
        BotLoopGuard guard = new BotLoopGuard(2, 60_000L, 5_000L);

        assertThat(guard.allow("peer-a")).isTrue();
        assertThat(guard.allow("peer-a")).isTrue();
        assertThat(guard.allow("peer-a")).isFalse();
        assertThat(guard.isCoolingDown("peer-a")).isTrue();
    }

    @Test
    void serviceCombinesDedupAndLoopProtection() {
        ChannelCatalogService service = new ChannelCatalogService();

        Map<String, Object> first = service.inspectInboundGuard("alice", "event-1");
        Map<String, Object> duplicate = service.inspectInboundGuard("alice", "event-1");

        assertThat(first.get("firstSeen")).isEqualTo(true);
        assertThat(first.get("withinLoopBudget")).isEqualTo(true);
        assertThat(duplicate.get("firstSeen")).isEqualTo(false);
        assertThat(duplicate.get("withinLoopBudget")).isEqualTo(false);
    }
}
