package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.registry.otlp.OtlpMetricsSender;

class OtlpMetricsDefaultConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class,
            CompositeMeterRegistryAutoConfiguration.class, OtlpMetricsExportAutoConfiguration.class, PrometheusMetricsExportAutoConfiguration.class))
            .withBean(OtlpMetricsSender.class, () -> mock(OtlpMetricsSender.class)).withInitializer(context -> {
                try {
                    var resource = new FileSystemResource("src/main/resources/config/application.yml");
                    for (var source : new YamlPropertySourceLoader().load("shipped-application", resource)) {
                        context.getEnvironment().getPropertySources().addLast(source);
                    }
                }
                catch (IOException e) {
                    throw new IllegalStateException("Cannot load the shipped application configuration", e);
                }
            });

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void disablesOtlpMetricsByDefaultWithoutDisablingPrometheus(boolean hyperionEnabled) {
        contextRunner.withPropertyValues("artemis.hyperion.enabled=" + hyperionEnabled).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(OtlpMeterRegistry.class).hasSingleBean(PrometheusMeterRegistry.class);
            var registry = context.getBean(PrometheusMeterRegistry.class);
            registry.counter("configuration.smoke").increment();
            assertThat(registry.scrape()).contains("configuration_smoke_total{application=\"Artemis\"} 1.0");
        });
    }

    @Test
    void allowsOperatorsToEnableOtlpMetricsExplicitly() {
        contextRunner.withPropertyValues("management.otlp.metrics.export.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OtlpMeterRegistry.class).hasSingleBean(PrometheusMeterRegistry.class);
            context.getBean(OtlpMeterRegistry.class).stop();
        });
    }
}
