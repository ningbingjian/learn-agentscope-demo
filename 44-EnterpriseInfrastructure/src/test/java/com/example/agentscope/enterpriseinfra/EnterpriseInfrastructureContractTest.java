package com.example.agentscope.enterpriseinfra;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentscope.enterpriseinfra.service.EnterpriseInfrastructureService;
import io.agentscope.extensions.scheduler.config.ScheduleConfig;
import io.agentscope.extensions.scheduler.config.ScheduleMode;
import org.junit.jupiter.api.Test;

class EnterpriseInfrastructureContractTest {

    @Test
    void officialInfrastructureAdaptersAreOnClasspath() {
        EnterpriseInfrastructureService service = new EnterpriseInfrastructureService();

        assertThat(service.components())
                .extracting(item -> item.get("name"))
                .contains(
                        "scheduler-quartz",
                        "scheduler-xxl-job",
                        "nacos-a2a",
                        "nacos-prompt",
                        "nacos-skill",
                        "higress");
    }

    @Test
    void scheduleConfigSupportsFixedRateAndCron() {
        ScheduleConfig fixedRate = ScheduleConfig.builder()
                .scheduleMode(ScheduleMode.FIXED_RATE)
                .fixedRate(5_000L)
                .build();
        ScheduleConfig cron = ScheduleConfig.builder()
                .scheduleMode(ScheduleMode.CRON)
                .cronExpression("0 0 8 * * ?")
                .build();

        assertThat(fixedRate.getScheduleMode()).isEqualTo(ScheduleMode.FIXED_RATE);
        assertThat(fixedRate.getFixedRate()).isEqualTo(5_000L);
        assertThat(cron.getScheduleMode()).isEqualTo(ScheduleMode.CRON);
        assertThat(cron.getCronExpression()).isEqualTo("0 0 8 * * ?");
    }

    @Test
    void webPreviewUsesTheSameOfficialScheduleConfig() {
        EnterpriseInfrastructureService service = new EnterpriseInfrastructureService();

        assertThat(service.scheduleExamples())
                .containsEntry("fixedRateMode", "FIXED_RATE")
                .containsEntry("cronMode", "CRON")
                .containsEntry("cronExpression", "0 0 8 * * ?");
    }
}
