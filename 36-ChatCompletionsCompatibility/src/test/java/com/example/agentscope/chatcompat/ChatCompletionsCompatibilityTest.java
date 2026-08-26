package com.example.agentscope.chatcompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.agentscope.core.chat.completions.streaming.ChatCompletionsStreamingAdapter;
import io.agentscope.spring.boot.chat.web.ChatCompletionsController;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ChatCompletionsCompatibilityApplication.class)
@AutoConfigureMockMvc
class ChatCompletionsCompatibilityTest {

    @Autowired MockMvc mockMvc;
    @Autowired ChatCompletionsController controller;
    @Autowired ChatCompletionsStreamingAdapter streamingAdapter;
    @Autowired AtomicInteger agentCreationCounter;

    @Test
    void starterExposesOpenAiCompatibleNonStreamingEndpoint() throws Exception {
        assertThat(controller).isNotNull();
        assertThat(streamingAdapter).isNotNull();

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model":"lesson-agent",
                                  "messages":[
                                    {"role":"user","content":"你好"}
                                  ],
                                  "stream":false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.role").value("assistant"))
                .andExpect(jsonPath("$.choices[0].message.content")
                        .value("Chat Completions demo reply: 你好"));
    }

    @Test
    void eachRequestCreatesAFreshPrototypeAgent() throws Exception {
        int before = agentCreationCounter.get();
        String request = """
                {
                  "model":"lesson-agent",
                  "messages":[{"role":"user","content":"ping"}],
                  "stream":false
                }
                """;

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        assertThat(agentCreationCounter.get() - before).isEqualTo(2);
    }
}
