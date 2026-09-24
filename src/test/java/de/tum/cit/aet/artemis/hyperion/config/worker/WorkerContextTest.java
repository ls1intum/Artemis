package de.tum.cit.aet.artemis.hyperion.config.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.aiworker.config.AiWorkerApplication;
import de.tum.cit.aet.artemis.aiworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.aiworker.service.WorkerSupervisorService;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerCommandListener;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerEventPublisher;
import de.tum.cit.aet.artemis.aiworker.service.sandbox.DockerSandboxService;

/** Boots the real worker scan and auto-configuration, replacing only external I/O. */
class WorkerContextTest {

    @ParameterizedTest
    @ValueSource(strings = { "aiworker,aiworker-standalone", "prod,aiworker,aiworker-standalone" })
    void standaloneWorkerDoesNotLoadServerModules(String profiles) {
        new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split(",")))
                .withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(AiWorkerApplication.class, ExternalServices.class)
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withPropertyValues("artemis.aiworker.workload=hyperion-generation", "artemis.aiworker.profile=java-gradle", "artemis.aiworker.id=context-test",
                        "artemis.aiworker.image=sha256:" + "a".repeat(64), "artemis.aiworker.max-concurrent-executions=4",
                        "spring.artemis.broker-url=tcp://broker.invalid:61617?sslEnabled=true", "spring.artemis.user=test", "spring.artemis.password=test",
                        "spring.ai.model.chat=none", "spring.ai.model.audio.transcription=none", "spring.ai.model.audio.speech=none", "spring.ai.model.embedding=none",
                        "spring.ai.model.image=none", "spring.ai.model.moderation=none", "management.tracing.export.otlp.enabled=false",
                        "management.logging.export.otlp.enabled=false", "management.otlp.metrics.export.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkerSupervisorService.class).hasSingleBean(DockerSandboxService.class)
                            .hasSingleBean(WorkerCommandListener.class);
                    assertThat(context).doesNotHaveBean("openAiEmbeddingModel").doesNotHaveBean("openAiImageModel").doesNotHaveBean("openAiSdkAudioSpeechModel")
                            .doesNotHaveBean("openAiSdkAudioTranscriptionModel").doesNotHaveBean("openAiSdkModerationModel");
                    assertThat(context.getBean(WorkerSettings.class).maxConcurrentExecutions()).isEqualTo(4);
                    for (String name : context.getBeanDefinitionNames()) {
                        Class<?> type = context.getType(name);
                        if (type != null) {
                            if (type.getName().startsWith("de.tum.cit.aet.artemis.")) {
                                assertThat(List.of("de.tum.cit.aet.artemis.aiworker.", "de.tum.cit.aet.artemis.hyperion.service.worker.",
                                        "de.tum.cit.aet.artemis.hyperion.config.worker.", "de.tum.cit.aet.artemis.hyperion.protocol.", "de.tum.cit.aet.artemis.hyperion.runtime."))
                                        .anyMatch(prefix -> type.getName().startsWith(prefix));
                            }
                            for (String forbidden : List.of("de.tum.cit.aet.artemis.core.", "de.tum.cit.aet.artemis.buildagent.", "de.tum.cit.aet.artemis.localci.",
                                    "de.tum.cit.aet.artemis.localvc.", "org.hibernate.", "com.hazelcast.", "org.redisson.", "org.springframework.data.",
                                    "org.springframework.boot.web.server.")) {
                                assertThat(type.getName()).doesNotStartWith(forbidden);
                            }
                        }
                    }
                    assertThat(context).doesNotHaveBean(javax.sql.DataSource.class);
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
        ChatModel testChatModel() {
            return mock(ChatModel.class);
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
