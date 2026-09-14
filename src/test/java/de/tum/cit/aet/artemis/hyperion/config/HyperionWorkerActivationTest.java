package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerClientService;

/** Exercises actual transport bean registration, not a test-only copy of the feature condition. */
class HyperionWorkerActivationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(HyperionWorkerMessagingConfiguration.class,
            GenerationWorkerClientService.class);

    @ParameterizedTest
    @CsvSource({ "false,true,core|localci|localvc", "true,false,core|localci|localvc", "true,true,localci|localvc", "true,true,core|localvc", "true,true,core|localci",
            "true,true,core|jenkins", "false,false,core" })
    void disabledOrIneligibleNodeNeedsNoBrokerConfiguration(boolean hyperion, boolean generation, String profiles) {
        runner.withPropertyValues("artemis.hyperion.enabled=" + hyperion, "artemis.hyperion.exercise-generation.enabled=" + generation)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split("\\|"))).run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(HyperionWorkerMessagingConfiguration.class).doesNotHaveBean(HyperionWorkerProperties.class)
                            .doesNotHaveBean(GenerationWorkerClientService.class).doesNotHaveBean(WorkerMessageCodec.class).doesNotHaveBean("hyperionConnectionFactory");
                });
    }

    @Test
    void eligibleCoreRegistersScopedTransportWithoutConnecting() {
        runner.withPropertyValues("artemis.hyperion.enabled=true", "artemis.hyperion.exercise-generation.enabled=true",
                "artemis.hyperion.workers.broker-url=tcp://broker.invalid:61617?sslEnabled=true", "artemis.hyperion.workers.user=core",
                "artemis.hyperion.workers.password=test-only", "artemis.hyperion.workers.ids=worker-1")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("core", "localci", "localvc")).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkerMessageCodec.class).hasSingleBean(GenerationWorkerClientService.class);
                    assertThat(context.getBean(HyperionWorkerProperties.class).ids()).containsExactly("worker-1");
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
