package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.ClassUtils;

import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerClientService;

/** Exercises actual transport bean registration, not a test-only copy of the feature condition. */
class HyperionWorkerActivationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(HyperionWorkerMessagingConfiguration.class,
            GenerationWorkerClientService.class);

    @ParameterizedTest
    @CsvSource({ "false,true,core|localci|localvc", "true,false,core|localci|localvc", "true,true,localci|localvc", "true,true,core|localvc", "true,true,core|localci",
            "true,true,core|jenkins", "false,false,core", "true,true,buildagent", "true,true,buildagent|localci|localvc" })
    void disabledOrIneligibleNodeNeedsNoBrokerConfiguration(boolean hyperion, boolean generation, String profiles) {
        runner.withPropertyValues("artemis.hyperion.enabled=" + hyperion, "artemis.hyperion.exercise-generation.enabled=" + generation)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split("\\|"))).run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(HyperionWorkerMessagingConfiguration.class).doesNotHaveBean(HyperionWorkerProperties.class)
                            .doesNotHaveBean(GenerationWorkerClientService.class).doesNotHaveBean(WorkerMessageCodec.class).doesNotHaveBean("hyperionConnectionFactory");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = { "core,localci,localvc", "buildagent,core,localci,localvc" })
    void eligibleCoreRegistersScopedTransportWithoutConnecting(String profiles) {
        runner.withPropertyValues("artemis.hyperion.enabled=true", "artemis.hyperion.exercise-generation.enabled=true",
                "artemis.hyperion.workers.broker-url=tcp://broker.invalid:61617?sslEnabled=true", "artemis.hyperion.workers.user=core",
                "artemis.hyperion.workers.password=test-only", "artemis.hyperion.workers.ids=worker-1")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split(","))).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkerMessageCodec.class).hasSingleBean(GenerationWorkerClientService.class);
                    assertThat(context.getBean(HyperionWorkerProperties.class).ids()).containsExactly("worker-1");
                    assertThat(ClassUtils.isPresent("de.tum.cit.aet.artemis.hyperionworker.HyperionWorkerApplication", context.getClassLoader())).isFalse();
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void shippedBuildAgentAndCombinedProfilesKeepGenerationOptIn(boolean combined) {
        String locations = "classpath:config/application-buildagent.yml" + (combined ? ",classpath:config/application-core.yml" : "");
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=" + locations, "spring.profiles.active=" + (combined ? "buildagent,core,localci,localvc" : "buildagent"))
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(GenerationWorkerClientService.class).doesNotHaveBean("hyperionConnectionFactory");
                    assertThat(context.getEnvironment().getProperty("artemis.hyperion.enabled", Boolean.class)).isFalse();
                    String[] exclusions = org.springframework.boot.context.properties.bind.Binder.get(context.getEnvironment()).bind("spring.autoconfigure.exclude", String[].class)
                            .orElseThrow(IllegalStateException::new);
                    assertThat(java.util.Arrays.stream(exclusions).anyMatch(name -> name.endsWith(".DataSourceAutoConfiguration"))).isEqualTo(!combined);
                });
    }

    @Test
    void optedInCoreCannotUseMissingWorkerCredentials() {
        runner.withPropertyValues("artemis.hyperion.enabled=true", "artemis.hyperion.exercise-generation.enabled=true")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("core", "localci", "localvc")).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThatThrownBy(() -> context.getBean(GenerationWorkerClientService.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }
}
