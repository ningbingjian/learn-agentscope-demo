package com.example.agentscope.modelregistry.model;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ToolSchema;
import reactor.core.publisher.Flux;

import java.util.List;

public final class LessonEchoModel implements Model {

    private final String modelId;
    private final ModelCreationContext creationContext;

    public LessonEchoModel(String modelId, ModelCreationContext creationContext) {
        this.modelId = modelId;
        this.creationContext = creationContext == null
                ? ModelCreationContext.empty()
                : creationContext;
    }

    @Override
    public Flux<ChatResponse> stream(
            List<Msg> messages,
            List<ToolSchema> tools,
            GenerateOptions options
    ) {
        String input = messages == null || messages.isEmpty()
                ? ""
                : messages.get(messages.size() - 1).getTextContent();
        String baseUrl = creationContext.getBaseUrl() == null
                ? "<provider-default>"
                : creationContext.getBaseUrl();
        String text = "lesson model=" + modelId
                + ", baseUrl=" + baseUrl
                + ", input=" + input;
        ContentBlock content = TextBlock.builder().text(text).build();
        return Flux.just(ChatResponse.builder()
                .content(List.of(content))
                .finishReason("stop")
                .build());
    }

    @Override
    public String getModelName() {
        return modelId;
    }

    public ModelCreationContext getCreationContext() {
        return creationContext;
    }
}
