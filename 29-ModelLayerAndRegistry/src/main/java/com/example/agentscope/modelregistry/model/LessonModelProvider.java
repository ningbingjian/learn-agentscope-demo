package com.example.agentscope.modelregistry.model;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.spi.ModelProvider;

public final class LessonModelProvider implements ModelProvider {

    @Override
    public String providerId() {
        return "lesson";
    }

    @Override
    public boolean supports(String modelId) {
        return modelId != null && modelId.startsWith("lesson:");
    }

    @Override
    public Model create(String modelId) {
        return new LessonEchoModel(modelId, ModelCreationContext.empty());
    }

    @Override
    public Model create(String modelId, ModelCreationContext context) {
        return new LessonEchoModel(modelId, context);
    }
}
