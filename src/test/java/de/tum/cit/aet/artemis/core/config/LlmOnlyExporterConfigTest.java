package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import io.opentelemetry.sdk.trace.export.SpanExporter;

class LlmOnlyExporterConfigTest {

    @ParameterizedTest
    @ValueSource(strings = { "legacy", "current", "both" })
    void bindsExporterSettingsAndPrefersCurrentNames(String configuration) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            Map<String, Object> properties = new HashMap<>(Map.of("management.langfuse.enabled", "true"));
            if (!"current".equals(configuration)) {
                properties.put("management.otlp.tracing.endpoint", "both".equals(configuration) ? "invalid-legacy-endpoint" : "https://example.test/v1/traces");
                properties.put("management.otlp.tracing.headers.Authorization", "Basic legacy");
            }
            if (!"legacy".equals(configuration)) {
                properties.put("management.opentelemetry.tracing.export.otlp.endpoint", "https://example.test/v1/traces");
                properties.put("management.opentelemetry.tracing.export.otlp.headers.Authorization", "Basic current");
            }
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
            context.register(LlmOnlyExporterConfig.class);
            context.refresh();

            assertThat(context.getBean("otlpLlmTraceExporter", SpanExporter.class)).isNotNull();
        }
    }
}
