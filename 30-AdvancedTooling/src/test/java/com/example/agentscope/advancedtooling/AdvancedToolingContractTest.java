package com.example.agentscope.advancedtooling;

import com.example.agentscope.advancedtooling.domain.RequestProfile;
import com.example.agentscope.advancedtooling.tool.ContextAwareTools;
import com.example.agentscope.advancedtooling.tool.DatabaseTools;
import com.example.agentscope.advancedtooling.tool.ProgressTools;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolGroup;
import io.agentscope.core.tool.ToolGroupScope;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancedToolingContractTest {

    @Test
    void injectedRuntimeContextAndPojoAreNotPartOfModelSchema() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ContextAwareTools());

        ToolSchema schema = toolkit.getToolSchemas().stream()
                .filter(item -> item.getName().equals("who_am_i"))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, Object> properties =
                (Map<String, Object>) schema.getParameters().get("properties");
        assertThat(properties).containsKey("prefix");
        assertThat(properties).doesNotContainKeys("context", "profile");

        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId("alice")
                .sessionId("session-a")
                .put(RequestProfile.class, new RequestProfile("acme", "zh-CN"))
                .build();
        ToolUseBlock call = ToolUseBlock.builder()
                .id("call-identity")
                .name("who_am_i")
                .input(Map.of("prefix", "identity:"))
                .build();

        ToolResultBlock result = toolkit.callTool(
                ToolCallParam.builder()
                        .toolUseBlock(call)
                        .runtimeContext(runtimeContext)
                        .build()
        ).block();

        assertThat(result).isNotNull();
        String text = ((TextBlock) result.getOutput().get(0)).getText();
        assertThat(text)
                .contains("userId=alice")
                .contains("sessionId=session-a")
                .contains("tenant=acme")
                .contains("locale=zh-CN");
    }

    @Test
    void toolEmitterPublishesProgressButFinalReturnRemainsSeparate() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ProgressTools());
        List<ToolResultBlock> chunks = new ArrayList<>();
        ToolUseBlock call = ToolUseBlock.builder()
                .id("call-progress")
                .name("progress_task")
                .input(Map.of("task", "index documents"))
                .build();

        ToolResultBlock result = toolkit.callTool(
                ToolCallParam.builder()
                        .toolUseBlock(call)
                        .emitter(chunks::add)
                        .build()
        ).block();

        assertThat(chunks).hasSize(2);
        assertThat(((TextBlock) chunks.get(0).getOutput().get(0)).getText())
                .contains("progress 25%");
        assertThat(((TextBlock) result.getOutput().get(0)).getText())
                .isEqualTo("completed: index documents");
    }

    @Test
    void inactiveToolGroupHidesSchemaUntilActivatedAndMetaToolIsRegistered() {
        Toolkit toolkit = new Toolkit();
        ToolGroup database = ToolGroup.builder()
                .name("database")
                .description("Database tools")
                .active(false)
                .scope(ToolGroupScope.META)
                .build();
        toolkit.registerToolGroup(database);
        toolkit.registration().tool(new DatabaseTools()).group("database").apply();

        assertThat(schemaNames(toolkit)).doesNotContain("db_lookup");

        toolkit.setActiveGroups(List.of("database"));
        assertThat(schemaNames(toolkit)).contains("db_lookup");

        toolkit.registerMetaTool();
        assertThat(toolkit.getToolNames()).contains("reset_equipped_tools");
    }

    private static List<String> schemaNames(Toolkit toolkit) {
        return toolkit.getToolSchemas().stream().map(ToolSchema::getName).toList();
    }
}
