package com.example.agentscope.a2aprotocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.a2a.spec.AgentCard;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.spring.boot.a2a.controller.A2aJsonRpcController;
import io.agentscope.spring.boot.a2a.controller.AgentCardController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = A2aProtocolApplication.class)
class A2aProtocolContractTest {

    @Autowired AgentScopeA2aServer server;
    @Autowired AgentCardController agentCardController;
    @Autowired A2aJsonRpcController jsonRpcController;
    @Autowired WebApplicationContext webApplicationContext;

    MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void starterCreatesServerAndA2aControllers() throws Exception {
        assertThat(server).isNotNull();
        assertThat(agentCardController).isNotNull();
        assertThat(jsonRpcController).isNotNull();

        AgentCard card = server.getAgentCard();
        assertThat(card.name()).isEqualTo("lesson-a2a-agent");
        assertThat(card.url()).isEqualTo("http://localhost:18081");
        assertThat(card.version()).isEqualTo("1.0.0");

        mockMvc.perform(get("/.well-known/agent-card.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("lesson-a2a-agent"))
                .andExpect(jsonPath("$.url").value("http://localhost:18081"));
    }

    @Test
    void remoteA2aAgentCanBeBuiltFromServerAgentCardWithoutCallingNetwork() {
        AgentCard card = server.getAgentCard();

        A2aAgent remote = A2aAgent.builder()
                .name("remote-wrapper")
                .agentCard(card)
                .build();

        assertThat(remote.getName()).isEqualTo("remote-wrapper");
        assertThat(remote.getDescription()).isEqualTo(card.description());
    }
}
