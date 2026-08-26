package com.example.agentscope.modelregistry.web;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.CachePolicy;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/model-registry")
public class ModelRegistryController {

    @GetMapping("/resolve")
    ResolveResponse resolve(
            @RequestParam(defaultValue = "lesson:echo") String modelId,
            @RequestParam(required = false) String baseUrl,
            @RequestParam(defaultValue = "false") boolean stream,
            @RequestParam(defaultValue = "false") boolean cache,
            @RequestParam(required = false) String cacheId
    ) {
        ModelCreationContext context = context(baseUrl, stream, cache, cacheId);
        Model first = ModelRegistry.resolve(modelId, context);
        Model second = ModelRegistry.resolve(modelId, context);
        return new ResolveResponse(
                modelId,
                ModelRegistry.canResolve(modelId, context),
                first.getClass().getSimpleName(),
                first.getModelName(),
                first == second,
                context.toString()
        );
    }

    @PostMapping("/chat")
    ChatResponse chat(@RequestBody ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
        String modelId = StringUtils.hasText(request.modelId())
                ? request.modelId()
                : "lesson:echo";
        ModelCreationContext context = ModelCreationContext.builder()
                .baseUrl(request.baseUrl())
                .stream(false)
                .cachePolicy(CachePolicy.DISABLED)
                .build();
        Model model = ModelRegistry.resolve(modelId, context);

        try (ReActAgent agent = ReActAgent.builder()
                .name("model-registry-agent")
                .sysPrompt("你是一个模型层学习助手，直接回答用户。")
                .model(model)
                .build()) {
            Msg reply = agent.call(new UserMessage(request.message())).block();
            if (reply == null) {
                throw new IllegalStateException("Agent returned no reply");
            }
            return new ChatResponse(modelId, model.getClass().getSimpleName(), reply.getTextContent());
        }
    }

    private static ModelCreationContext context(
            String baseUrl,
            boolean stream,
            boolean cache,
            String cacheId
    ) {
        ModelCreationContext.Builder builder = ModelCreationContext.builder()
                .baseUrl(baseUrl)
                .stream(stream)
                .cachePolicy(cache ? CachePolicy.ENABLED : CachePolicy.DEFAULT);
        if (cache && StringUtils.hasText(cacheId)) {
            builder.cacheId(cacheId);
        }
        return builder.build();
    }

    public record ResolveResponse(
            String modelId,
            boolean canResolve,
            String modelClass,
            String modelName,
            boolean sameInstanceOnSecondResolve,
            String creationContext
    ) {
    }

    public record ChatRequest(String modelId, String message, String baseUrl) {
    }

    public record ChatResponse(String modelId, String modelClass, String reply) {
    }
}
