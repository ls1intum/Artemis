package de.tum.cit.aet.artemis.hyperionworker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.hyperionworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.hyperionworker.messaging.WorkerCommandListener;
import de.tum.cit.aet.artemis.hyperionworker.messaging.WorkerEventPublisher;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.DockerSandbox;
import de.tum.cit.aet.artemis.hyperionworker.session.WorkerSupervisor;

/** Boots the real worker scan and auto-configuration, replacing only external I/O. */
class WorkerContextTest {

    @ParameterizedTest
    @ValueSource(strings = { "worker", "buildagent", "core,localci,localvc,buildagent" })
    void serverProfilesCannotLoadServerModules(String profiles) {
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(HyperionWorkerApplication.class, ExternalServices.class)
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withPropertyValues("spring.profiles.active=" + profiles, "artemis.hyperion.worker.id=context-test", "artemis.hyperion.worker.image=sha256:" + "a".repeat(64),
                        "artemis.hyperion.worker.max-concurrent-generations=4", "spring.artemis.broker-url=tcp://broker.invalid:61617?sslEnabled=true", "spring.artemis.user=test",
                        "spring.artemis.password=test", "spring.ai.openai.api-key=test-only", "management.tracing.export.otlp.enabled=false",
                        "management.logging.export.otlp.enabled=false", "management.otlp.metrics.export.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkerSupervisor.class).hasSingleBean(DockerSandbox.class).hasSingleBean(WorkerCommandListener.class);
                    assertThat(context).doesNotHaveBean("openAiEmbeddingModel").doesNotHaveBean("openAiImageModel").doesNotHaveBean("openAiSdkAudioSpeechModel")
                            .doesNotHaveBean("openAiSdkAudioTranscriptionModel").doesNotHaveBean("openAiSdkModerationModel");
                    assertThat(context.getBean(WorkerSettings.class).maxConcurrentGenerations()).isEqualTo(4);
                    for (String name : context.getBeanDefinitionNames()) {
                        Class<?> type = context.getType(name);
                        if (type != null) {
                            if (type.getName().startsWith("de.tum.cit.aet.artemis.")) {
                                assertThat(
                                        List.of("de.tum.cit.aet.artemis.hyperionworker.", "de.tum.cit.aet.artemis.hyperion.protocol.", "de.tum.cit.aet.artemis.hyperion.runtime."))
                                        .anyMatch(prefix -> type.getName().startsWith(prefix));
                            }
                            for (String forbidden : List.of("de.tum.cit.aet.artemis.core.", "de.tum.cit.aet.artemis.buildagent.", "de.tum.cit.aet.artemis.localci.",
                                    "de.tum.cit.aet.artemis.localvc.", "org.hibernate.", "com.hazelcast.", "org.redisson.", "org.springframework.data.",
                                    "org.springframework.boot.web.server.")) {
                                assertThat(type.getName()).doesNotStartWith(forbidden);
                            }
                        }
                    }
                    assertThat(context.getClassLoader().getResource("config/application-core.yml")).isNull();
                    assertThat(context.getClassLoader().getResource("config/liquibase/master.xml")).isNull();
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExternalServices {

        @Bean
        @Primary
        DockerClient testDockerClient() {
            DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);
            when(docker.listContainersCmd().withShowAll(true).exec()).thenReturn(List.of());
            when(docker.inspectImageCmd("sha256:" + "a".repeat(64)).exec().getId()).thenReturn("sha256:" + "a".repeat(64));
            return docker;
        }

        @Bean
        @Primary
        WorkerEventPublisher testPublisher() {
            return event -> {
            };
        }

        @Bean
        static BeanPostProcessor disableExternalCommandConsumption() {
            return new BeanPostProcessor() {

                @Override
                public Object postProcessBeforeInitialization(Object bean, String name) {
                    if (bean instanceof DefaultJmsListenerContainerFactory factory) {
                        factory.setAutoStartup(false);
                    }
                    return bean;
                }
            };
        }
    }
}
