package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

class WorkerSettingsBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(SettingsConfiguration.class).withPropertyValues(
            "artemis.aiworker.workload=text-analysis", "artemis.aiworker.profile=text", "artemis.aiworker.id=worker-1", "artemis.aiworker.image=sha256:" + "a".repeat(64));

    @Test
    void bindsDefaultCapacity() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerSettings.class).maxConcurrentExecutions()).isEqualTo(1);
        });
    }

    @Test
    void bindsProfileWithoutAssumingJava() {
        runner.withPropertyValues("artemis.aiworker.profile=python-pytest").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerSettings.class).profile()).isEqualTo("python-pytest");
        });
    }

    @Test
    void rejectsMalformedProfile() {
        runner.withPropertyValues("artemis.aiworker.profile=../escape").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bindsFourSlots() {
        runner.withPropertyValues("artemis.aiworker.max-concurrent-executions=4").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerSettings.class).maxConcurrentExecutions()).isEqualTo(4);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Lazy
    @EnableConfigurationProperties(WorkerSettings.class)
    static class SettingsConfiguration {
    }
}
