package com.example.agentscope.structuredoutput;

import com.example.agentscope.structuredoutput.domain.TicketAnalysis;
import io.agentscope.core.message.MessageMetadataKeys;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredOutputContractTest {

    @Test
    void convertsStructuredMetadataIntoJavaRecord() {
        Map<String, Object> structured = Map.of(
                "category", "PAYMENT",
                "priority", "HIGH",
                "summary", "用户扣款后订单未到账",
                "needHuman", true
        );

        Msg reply = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .textContent("structured result")
                .metadata(Map.of(MessageMetadataKeys.STRUCTURED_OUTPUT, structured))
                .build();

        TicketAnalysis analysis = reply.getStructuredData(TicketAnalysis.class);

        assertThat(reply.hasStructuredData()).isTrue();
        assertThat(analysis.category()).isEqualTo("PAYMENT");
        assertThat(analysis.priority()).isEqualTo("HIGH");
        assertThat(analysis.summary()).isEqualTo("用户扣款后订单未到账");
        assertThat(analysis.needHuman()).isTrue();
    }
}
