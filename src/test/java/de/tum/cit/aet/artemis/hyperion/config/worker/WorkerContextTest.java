package de.tum.cit.aet.artemis.hyperion.config.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.DispatcherServlet;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.ArtemisApp;
import de.tum.cit.aet.artemis.aiworker.api.AiWorkerApi;
import de.tum.cit.aet.artemis.aiworker.api.ExecutionObserver;
import de.tum.cit.aet.artemis.aiworker.api.WorkloadApi;
import de.tum.cit.aet.artemis.aiworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.aiworker.service.WorkerClientService;
import de.tum.cit.aet.artemis.aiworker.service.WorkerRegistryService;
import de.tum.cit.aet.artemis.aiworker.service.WorkerSupervisorService;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerCommandListener;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerEventPublisher;
import de.tum.cit.aet.artemis.aiworker.service.sandbox.DockerSandboxService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.hyperion.service.worker.HyperionWorkloadService;

/** Checks the worker scan boundary with external services replaced by test beans. */
class WorkerContextTest {

    @ParameterizedTest
    @ValueSource(strings = { "aiworker", "prod,aiworker" })
    void standaloneWorkerDoesNotLoadServerModules(String profiles) {
        new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split(",")))
                .withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(ArtemisApp.class, ExternalServices.class, HyperionModel.class)
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withPropertyValues("artemis.aiworker.workload=hyperion-generation", "artemis.aiworker.profile=java-gradle", "artemis.aiworker.id=context-test",
                        "artemis.aiworker.image=sha256:" + "a".repeat(64), "artemis.aiworker.max-concurrent-executions=4", "artemis.distributed-data.provider=local",
                        "spring.ai.model.chat=none", "spring.ai.model.audio.transcription=none", "spring.ai.model.audio.speech=none", "spring.ai.model.embedding=none",
                        "spring.ai.model.image=none", "spring.ai.model.moderation=none", "management.tracing.export.otlp.enabled=false",
                        "management.logging.export.otlp.enabled=false", "management.otlp.metrics.export.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkerSupervisorService.class).hasSingleBean(DockerSandboxService.class)
                            .hasSingleBean(WorkerCommandListener.class).hasSingleBean(HyperionWorkloadService.class);
                    assertThat(context.getEnvironment().getProperty("spring.main.web-application-type")).isEqualTo("none");
                    assertThat(context.getEnvironment().getProperty("spring.hazelcast.localInstances")).isEqualTo("false");
                    assertThat(context).doesNotHaveBean("openAiEmbeddingModel").doesNotHaveBean("openAiImageModel").doesNotHaveBean("openAiSdkAudioSpeechModel")
                            .doesNotHaveBean("openAiSdkAudioTranscriptionModel").doesNotHaveBean("openAiSdkModerationModel");
                    assertThat(context.getBean(WorkerSettings.class).maxConcurrentExecutions()).isEqualTo(4);
                    assertThat(context.getBeanNamesForAnnotation(Controller.class)).isEmpty();
                    assertThat(context.getBeanNamesForAnnotation(RestController.class)).isEmpty();
                    assertThat(context).doesNotHaveBean(DispatcherServlet.class).doesNotHaveBean(AiWorkerApi.class).doesNotHaveBean(WorkerClientService.class)
                            .doesNotHaveBean(WorkerRegistryService.class).doesNotHaveBean(ServletWebServerFactory.class);
                    for (String name : context.getBeanDefinitionNames()) {
                        Class<?> type = context.getType(name);
                        if (type != null) {
                            for (String forbidden : List.of("de.tum.cit.aet.artemis.buildagent.", "de.tum.cit.aet.artemis.localci.", "de.tum.cit.aet.artemis.localvc.",
                                    "de.tum.cit.aet.artemis.core.web.", "de.tum.cit.aet.artemis.hyperion.web.", "de.tum.cit.aet.artemis.programming.",
                                    "de.tum.cit.aet.artemis.exercise.", "de.tum.cit.aet.artemis.exam.", "org.hibernate.")) {
                                assertThat(type.getName()).doesNotStartWith(forbidden);
                            }
                        }
                    }
                    assertThat(context).doesNotHaveBean(javax.sql.DataSource.class).doesNotHaveBean(EntityManagerFactory.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = { "aiworker", "prod,aiworker" })
    void genericWorkerDoesNotLoadHyperionOrModelsWithInheritedServerModelSettings(String profiles) {
        new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split(",")))
                .withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(ArtemisApp.class, ExternalServices.class)
                .withBean(WorkloadApi.class, GenericWorkload::new)
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withPropertyValues("artemis.aiworker.workload=document-check", "artemis.aiworker.profile=plain-text", "artemis.aiworker.id=context-test",
                        "artemis.aiworker.image=sha256:" + "a".repeat(64), "artemis.distributed-data.provider=local", "artemis.aiworker.enabled=true",
                        "artemis.hyperion.enabled=true", "artemis.hyperion.exercise-generation.enabled=true", "artemis.atlas.enabled=true", "spring.ai.model.chat=openai",
                        "spring.ai.openai.api-key=worker-isolation-test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkerSupervisorService.class).hasSingleBean(WorkloadApi.class);
                    assertThat(context.getEnvironment().getProperty("spring.ai.model.chat")).isEqualTo("openai");
                    assertThat(context.getBean(WorkloadApi.class).capability()).isEqualTo(new WorkloadCapabilityDTO("document-check", 1, "plain-text"));
                    assertThat(context).doesNotHaveBean(HyperionWorkloadService.class).doesNotHaveBean(ChatModel.class).doesNotHaveBean(AiWorkerApi.class)
                            .doesNotHaveBean(WorkerClientService.class).doesNotHaveBean(WorkerRegistryService.class).doesNotHaveBean(javax.sql.DataSource.class)
                            .doesNotHaveBean(EntityManagerFactory.class).doesNotHaveBean(ServletWebServerFactory.class);
                    assertThat(context).doesNotHaveBean("openAiEmbeddingModel").doesNotHaveBean("openAiImageModel").doesNotHaveBean("openAiSdkAudioSpeechModel")
                            .doesNotHaveBean("openAiSdkAudioTranscriptionModel").doesNotHaveBean("openAiSdkModerationModel");
                    assertThat(context.getBeanNamesForAnnotation(Controller.class)).isEmpty();
                    assertThat(context.getBeanNamesForAnnotation(RestController.class)).isEmpty();
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExternalServices {

        @Bean
        @Primary
        DistributedDataProvider testProvider() {
            return mock(DistributedDataProvider.class);
        }

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
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HyperionModel {

        @Bean
        ChatModel testChatModel() {
            return mock(ChatModel.class);
        }
    }

    static class GenericWorkload implements WorkloadApi {

        @Override
        public WorkloadCapabilityDTO capability() {
            return new WorkloadCapabilityDTO("document-check", 1, "plain-text");
        }

        @Override
        public String execute(ExecutionAssignmentDTO assignment, BooleanSupplier stopping, ExecutionObserver observer, Consumer<String> checkpoint) {
            return "checked";
        }
    }
}
