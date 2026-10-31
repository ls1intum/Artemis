package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;

import de.tum.cit.aet.artemis.aiworker.dto.SandboxPolicyDTO;

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

    @Test
    void shippedWorkerProfileSelectsHyperionGenerationWithoutWorkloadSettings() {
        new ApplicationContextRunner().withUserConfiguration(SettingsConfiguration.class).withInitializer(context -> {
            try {
                new YamlPropertySourceLoader().load("aiworker", new ClassPathResource("config/application-aiworker.yml"))
                        .forEach(context.getEnvironment().getPropertySources()::addLast);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).withPropertyValues("AI_WORKER_ID=worker-1", "AI_WORKER_SANDBOX_IMAGE=sha256:" + "a".repeat(64)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerSettings.class).workload()).isEqualTo("hyperion-generation");
            assertThat(context.getBean(WorkerSettings.class).profile()).isEqualTo("java-gradle");
        });
    }

    @Test
    void shippedWorkerProfileStartsWithoutAConfiguredSandboxImage() {
        DockerClient docker = mock();
        InspectImageCmd inspect = mock();
        InspectImageResponse image = mock();
        String imageId = "sha256:" + "b".repeat(64);
        when(docker.inspectImageCmd("ghcr.io/ls1intum/artemis-hyperion-sandbox:java25-1")).thenReturn(inspect);
        when(inspect.exec()).thenReturn(image);
        when(image.getId()).thenReturn(imageId);
        new ApplicationContextRunner().withUserConfiguration(SettingsConfiguration.class, WorkerSandboxConfiguration.class).withBean(DockerClient.class, () -> docker)
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles("aiworker");
                    try {
                        new YamlPropertySourceLoader().load("aiworker", new ClassPathResource("config/application-aiworker.yml"))
                                .forEach(context.getEnvironment().getPropertySources()::addLast);
                    }
                    catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).withPropertyValues("AI_WORKER_ID=worker-1").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(WorkerSettings.class).image()).isEqualTo("ghcr.io/ls1intum/artemis-hyperion-sandbox:java25-1");
                    assertThat(context.getBean(SandboxPolicyDTO.class).image()).isEqualTo(imageId);
                });
    }

    @Test
    void acceptsAConfiguredVersionedImage() {
        runner.withPropertyValues("artemis.aiworker.image=ghcr.io/ls1intum/artemis-hyperion-sandbox:java25-2").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerSettings.class).image()).isEqualTo("ghcr.io/ls1intum/artemis-hyperion-sandbox:java25-2");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "sha256:short", "sha256:", "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "sha256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" })
    void rejectsMalformedLocalImageIds(String image) {
        runner.withPropertyValues("artemis.aiworker.image=" + image).run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @Lazy
    @EnableConfigurationProperties(WorkerSettings.class)
    static class SettingsConfiguration {
    }
}
