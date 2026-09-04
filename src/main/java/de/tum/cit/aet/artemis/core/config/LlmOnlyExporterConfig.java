package de.tum.cit.aet.artemis.core.config;

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/** Exports only spans tagged {@code ai.span=true} through OTLP. */
@Lazy
@Configuration
@ConditionalOnProperty(prefix = "management.langfuse", name = "enabled", havingValue = "true")
public class LlmOnlyExporterConfig {

    private static final AttributeKey<String> AI_SPAN = AttributeKey.stringKey("ai.span");

    /**
     * Creates a filtered OTLP exporter. Disable Spring Boot's default OTLP exporter to avoid duplicate spans.
     *
     * @param endpoint   the OTLP endpoint for Langfuse (e.g., https://langfuse.de/api/public/otel/v1/traces)
     * @param authHeader the authorization header for Langfuse in the form {@code "Basic <base64(public:secret)>"}
     * @return a {@link SpanExporter} that exports only spans with {@code ai.span=true}
     */
    @Bean(name = "otlpLlmTraceExporter")
    public SpanExporter llmOnlyOtlpExporter(@Value("${management.opentelemetry.tracing.export.otlp.endpoint}") String endpoint,
            @Value("${management.opentelemetry.tracing.export.otlp.headers.Authorization}") String authHeader) {

        SpanExporter delegate = OtlpHttpSpanExporter.builder().setEndpoint(endpoint).addHeader("Authorization", authHeader).build();

        return new SpanExporter() {

            @Override
            public CompletableResultCode export(Collection<SpanData> spans) {
                List<SpanData> llmSpans = spans.stream().filter(LlmOnlyExporterConfig::isLlmSpan).toList();
                return llmSpans.isEmpty() ? CompletableResultCode.ofSuccess() : delegate.export(llmSpans);
            }

            @Override
            public CompletableResultCode flush() {
                return delegate.flush();
            }

            @Override
            public CompletableResultCode shutdown() {
                return delegate.shutdown();
            }
        };
    }

    private static boolean isLlmSpan(SpanData span) {
        return "true".equals(span.getAttributes().get(AI_SPAN));
    }
}
