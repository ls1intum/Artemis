package de.tum.cit.aet.artemis.hyperion.langfuse;

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Custom OpenTelemetry span exporter configuration that filters and sends
 * only AI-related (LLM) spans to Langfuse.
 *
 * <p>
 * This configuration replaces Spring Boot’s default OTLP HTTP exporter.
 * It ensures that only spans tagged with {@code ai.span=true} are exported,
 * so infrastructure spans (HTTP, DB, etc.) are ignored.
 */
@Configuration
public class LlmOnlyExporterConfig {

    /**
     * Attribute key used to identify AI-related spans.
     * Spans marked with this attribute (by the ObservationFilter)
     * are exported to Langfuse.
     */
    private static final AttributeKey<String> AI_SPAN = AttributeKey.stringKey("ai.span");

    /**
     * Defines a custom {@link SpanExporter} bean that wraps the default
     * {@link OtlpHttpSpanExporter} and filters out non-AI spans.
     *
     * <p>
     * The bean is explicitly named {@code otlpHttpSpanExporter} so that
     * it overrides Spring Boot’s built-in OTLP exporter. This prevents
     * duplicate exporters and ensures only this filtered exporter runs.
     *
     * @param endpoint   the OTLP endpoint for Langfuse (e.g., https://langfuse.de/api/public/otel/v1/traces)
     * @param authHeader the authorization header for Langfuse in the form {@code "Basic <base64(public:secret)>"}
     * @return a {@link SpanExporter} that exports only spans with {@code ai.span=true}
     */
    @Bean(name = "otlpHttpSpanExporter")
    public SpanExporter llmOnlyOtlpExporter(@Value("${management.otlp.tracing.endpoint}") String endpoint,
            @Value("${management.otlp.tracing.headers.Authorization}") String authHeader) {

        SpanExporter delegate = OtlpHttpSpanExporter.builder().setEndpoint(endpoint).addHeader("Authorization", authHeader).build();

        return new SpanExporter() {

            /**
             * Called whenever spans are ready to be exported.
             * Filters only AI-related spans before delegating to the actual exporter.
             */
            @Override
            public CompletableResultCode export(Collection<SpanData> spans) {
                List<SpanData> llmSpans = spans.stream().filter(LlmOnlyExporterConfig::isLlmSpan).toList();

                // If nothing matched, we succeed without exporting.
                return llmSpans.isEmpty() ? CompletableResultCode.ofSuccess() : delegate.export(llmSpans);
            }

            /** Flush any buffered spans in the delegate exporter. */
            @Override
            public CompletableResultCode flush() {
                return delegate.flush();
            }

            /** Shut down the delegate exporter gracefully. */
            @Override
            public CompletableResultCode shutdown() {
                return delegate.shutdown();
            }
        };
    }

    /**
     * Determines whether a given span is AI-related by checking
     * the {@code ai.span} attribute added by the ObservationFilter.
     *
     * @param span the span to inspect
     * @return {@code true} if {@code ai.span=true}, otherwise {@code false}
     */
    private static boolean isLlmSpan(SpanData span) {
        Attributes a = span.getAttributes();
        return "true".equals(a.get(AI_SPAN));
    }
}
