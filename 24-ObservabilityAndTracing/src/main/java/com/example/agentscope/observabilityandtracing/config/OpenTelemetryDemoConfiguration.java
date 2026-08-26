package com.example.agentscope.observabilityandtracing.config;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "demo.observability.otel-enabled",
        havingValue = "true"
)
public class OpenTelemetryDemoConfiguration {

    @Bean(destroyMethod = "close")
    SdkTracerProvider demoTracerProvider() {
        return SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                .build();
    }

    @Bean
    OpenTelemetrySdk demoOpenTelemetry(SdkTracerProvider demoTracerProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(demoTracerProvider)
                .buildAndRegisterGlobal();
    }
}
