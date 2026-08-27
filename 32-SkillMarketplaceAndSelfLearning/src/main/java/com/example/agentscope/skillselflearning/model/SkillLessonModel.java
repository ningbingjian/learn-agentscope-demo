package com.example.agentscope.skillselflearning.model;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import reactor.core.publisher.Flux;

/** A deterministic model: the lesson focuses on skill governance, not LLM behavior. */
public final class SkillLessonModel implements Model {

    @Override
    public Flux<ChatResponse> stream(
            List<Msg> messages,
            List<ToolSchema> tools,
            GenerateOptions options) {
        ContentBlock block = TextBlock.builder()
                .text("skill governance lesson model")
                .build();
        return Flux.just(ChatResponse.builder()
                .content(List.of(block))
                .finishReason("stop")
                .build());
    }

    @Override
    public String getModelName() {
        return "lesson-skill-governance-model";
    }
}
