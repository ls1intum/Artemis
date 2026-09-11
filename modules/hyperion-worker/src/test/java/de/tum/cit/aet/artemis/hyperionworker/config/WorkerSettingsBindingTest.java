package de.tum.cit.aet.artemis.hyperionworker.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class WorkerSettingsBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(SettingsConfiguration.class)
            .withPropertyValues("artemis.hyperion.worker.id=worker-1", "artemis.hyperion.worker.image=sha256:" + "a".repeat(64));

    @Test
    void bindsDefaultCapacity() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerSettings.class).maxConcurrentGenerations()).isEqualTo(1);
        });
    }

    @Test
    void bindsFourSlots() {
        runner.withPropertyValues("artemis.hyperion.worker.max-concurrent-generations=4").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerSettings.class).maxConcurrentGenerations()).isEqualTo(4);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WorkerSettings.class)
    static class SettingsConfiguration {
    }
}
