package com.example.agentscope.enterpriseinfra.service;

import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistry;
import io.agentscope.core.nacos.prompt.NacosPromptListener;
import io.agentscope.core.nacos.skill.NacosSkillRepository;
import io.agentscope.extensions.higress.HigressMcpClientBuilder;
import io.agentscope.extensions.higress.HigressToolkit;
import io.agentscope.extensions.scheduler.config.ScheduleConfig;
import io.agentscope.extensions.scheduler.config.ScheduleMode;
import io.agentscope.extensions.scheduler.quartz.QuartzAgentScheduler;
import io.agentscope.extensions.scheduler.xxljob.XxlJobAgentScheduler;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EnterpriseInfrastructureService {

    public List<Map<String, Object>> components() {
        return List.of(
                component("scheduler-quartz", "agentscope-extensions-scheduler-quartz",
                        QuartzAgentScheduler.class, "Local or clustered Quartz scheduling"),
                component("scheduler-xxl-job", "agentscope-extensions-scheduler-xxl-job",
                        XxlJobAgentScheduler.class, "Distributed scheduling with XXL-Job admin"),
                component("nacos-a2a", "agentscope-extensions-nacos-a2a",
                        NacosA2aRegistry.class, "A2A AgentCard registration and discovery"),
                component("nacos-a2a-resolver", "agentscope-extensions-nacos-a2a",
                        NacosAgentCardResolver.class, "Resolve remote A2A AgentCard from Nacos"),
                component("nacos-prompt", "agentscope-extensions-nacos-prompt",
                        NacosPromptListener.class, "Prompt template hot update"),
                component("nacos-skill", "agentscope-extensions-nacos-skill",
                        NacosSkillRepository.class, "Read-only Nacos AI skill packages"),
                component("higress", "agentscope-extensions-higress",
                        HigressToolkit.class, "MCP tools governed by Higress AI Gateway"),
                component("higress-client", "agentscope-extensions-higress",
                        HigressMcpClientBuilder.class, "Build streamable HTTP MCP client")
        );
    }

    public Map<String, Object> scheduleExamples() {
        ScheduleConfig fixedRate = ScheduleConfig.builder()
                .scheduleMode(ScheduleMode.FIXED_RATE)
                .fixedRate(5_000L)
                .initialDelay(1_000L)
                .build();

        ScheduleConfig cron = ScheduleConfig.builder()
                .scheduleMode(ScheduleMode.CRON)
                .cronExpression("0 0 8 * * ?")
                .build();

        return Map.of(
                "fixedRateMode", fixedRate.getScheduleMode().name(),
                "fixedRateMillis", fixedRate.getFixedRate(),
                "initialDelayMillis", fixedRate.getInitialDelay(),
                "cronMode", cron.getScheduleMode().name(),
                "cronExpression", cron.getCronExpression()
        );
    }

    private Map<String, Object> component(
            String name, String artifact, Class<?> type, String role) {
        return Map.of(
                "name", name,
                "artifact", artifact,
                "class", type.getName(),
                "role", role
        );
    }
}
