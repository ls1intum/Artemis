package de.tum.cit.aet.artemis.hyperionworker.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

class WorkerTelemetryConfigurationTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void bootExportsParentedChatSpansAndHonorsTheContentOptIn(boolean captureContent) {
        var spans = new ArrayList<SpanData>();
        SpanExporter exporter = new SpanExporter() {

            @Override
            public CompletableResultCode export(java.util.Collection<SpanData> batch) {
                spans.addAll(batch);
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ObservationAutoConfiguration.class, MicrometerTracingAutoConfiguration.class, OpenTelemetryTracingAutoConfiguration.class,
                        OpenTelemetrySdkAutoConfiguration.class))
                .withUserConfiguration(WorkerTelemetryConfiguration.class).withBean(SpanProcessor.class, () -> SimpleSpanProcessor.create(exporter))
                .withPropertyValues("management.tracing.sampling.probability=1.0", "artemis.telemetry.gen-ai.capture-content=" + captureContent).run(context -> {
                    assertThat(context).hasNotFailed();
                    var registry = context.getBean(ObservationRegistry.class);
                    var chat = ChatModelObservationContext.builder().prompt(new Prompt("private teaching brief")).provider("openai").build();
                    Observation.createNotStarted("hyperion.generation", registry).highCardinalityKeyValue("artemis.hyperion.job.id", "job")
                            .observe(() -> Observation.createNotStarted("gen_ai.client.operation", () -> chat, registry).observe(() -> {
                            }));

                    assertThat(spans).hasSize(2);
                    SpanData child = spans.getFirst();
                    SpanData parent = spans.getLast();
                    assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
                    assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
                    assertThat(parent.getAttributes().get(AttributeKey.stringKey("artemis.hyperion.job.id"))).isEqualTo("job");
                    assertThat(child.getAttributes().get(AttributeKey.stringKey("ai.span"))).isEqualTo("true");
                    String content = child.getAttributes().get(AttributeKey.stringKey("gen_ai.input.messages"));
                    if (captureContent) {
                        assertThat(content).contains("private teaching brief");
                        assertThat(child.getAttributes().get(AttributeKey.stringKey("artemis.gen_ai.content.complete"))).isEqualTo("true");
                    }
                    else {
                        assertThat(content).isNull();
                        assertThat(child.getAttributes().asMap().values()).noneMatch(value -> value.toString().contains("private teaching brief"));
                    }
                });
    }
}
