package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import io.opentelemetry.sdk.trace.export.SpanExporter;

class LlmOnlyExporterConfigTest {

    @Test
    void bindsSpringBootFourOtlpSettingsForCustomExporter() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test", Map.of("management.langfuse.enabled", "true", "management.opentelemetry.tracing.export.otlp.endpoint",
                            "https://example.test/v1/traces", "management.opentelemetry.tracing.export.otlp.headers.Authorization", "Basic test")));
            context.register(LlmOnlyExporterConfig.class);
            context.refresh();

            assertThat(context.getBean("otlpLlmTraceExporter", SpanExporter.class)).isNotNull();
        }
    }
}
