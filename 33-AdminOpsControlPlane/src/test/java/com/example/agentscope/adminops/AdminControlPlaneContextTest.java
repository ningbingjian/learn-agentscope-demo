package com.example.agentscope.adminops;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.spring.boot.admin.endpoint.AgentscopeAgentsEndpoint;
import io.agentscope.spring.boot.admin.endpoint.AgentscopeModelsEndpoint;
import io.agentscope.spring.boot.admin.endpoint.AgentscopeStatusEndpoint;
import io.agentscope.spring.boot.admin.endpoint.AgentscopeToolsEndpoint;
import io.agentscope.spring.boot.admin.endpoint.AgentscopeUsageEndpoint;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = AdminOpsControlPlaneApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "agentscope.admin.enabled=true",
            "agentscope.admin.write-enabled=false"
        })
class AdminControlPlaneContextTest {

    @Autowired AgentscopeStatusEndpoint statusEndpoint;
    @Autowired AgentscopeAgentsEndpoint agentsEndpoint;
    @Autowired AgentscopeToolsEndpoint toolsEndpoint;
    @Autowired AgentscopeModelsEndpoint modelsEndpoint;
    @Autowired AgentscopeUsageEndpoint usageEndpoint;

    @Test
    void adminStarterBuildsReadOnlyInventoryControlPlane() {
        Map<String, Object> status = statusEndpoint.status();
        assertThat(status.get("admin_enabled")).isEqualTo(true);
        assertThat(status.get("admin_write_enabled")).isEqualTo(false);

        assertThat(agentsEndpoint.agents()).isNotEmpty();
        assertThat(toolsEndpoint.tools())
                .containsKey("adminDemoToolkit");
        assertThat(toolsEndpoint.tools().get("adminDemoToolkit"))
                .contains("system_info");
        assertThat(modelsEndpoint.models())
                .containsEntry("adminDemoModel", "lesson-admin-demo-model");

        Map<String, Object> usage = usageEndpoint.usage();
        assertThat(usage).containsKeys("global", "by_agent", "by_model");
    }
}
