package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.aiworker.api.ExecutionObserver;
import de.tum.cit.aet.artemis.aiworker.api.WorkloadApi;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerCommandType;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.aiworker.service.WorkerSupervisorService;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerEventPublisher;

/** Boots and executes a second workload with Hyperion absent, not merely a mocked domain engine. */
class WorkerIsolationTest {

    @Test
    void executesWithoutHyperionOrModelCredentials() {
        String image = "sha256:" + "a".repeat(64);
        var capability = new WorkloadCapabilityDTO("document-check", 1, "plain-text");
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);
        when(docker.listContainersCmd().withShowAll(true).exec()).thenReturn(List.of());
        when(docker.inspectImageCmd(image).exec().getId()).thenReturn(image);
        new ApplicationContextRunner().withClassLoader(new FilteredClassLoader("de.tum.cit.aet.artemis.hyperion")).withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(AiWorkerApplication.class).withBean("testDocker", DockerClient.class, () -> docker, definition -> definition.setPrimary(true))
                .withBean("testPublisher", WorkerEventPublisher.class, () -> events::add, definition -> definition.setPrimary(true))
                .withBean(BeanPostProcessor.class, () -> new BeanPostProcessor() {

                    @Override
                    public Object postProcessBeforeInitialization(Object bean, String name) {
                        if (bean instanceof DefaultJmsListenerContainerFactory factory) {
                            factory.setAutoStartup(false);
                        }
                        return bean;
                    }
                }).withBean(WorkloadApi.class, () -> new WorkloadApi() {

                    @Override
                    public WorkloadCapabilityDTO capability() {
                        return capability;
                    }

                    @Override
                    public String execute(ExecutionAssignmentDTO assignment, BooleanSupplier stopping, ExecutionObserver observer, Consumer<String> checkpoint) {
                        observer.progress("Checked document", "evidence", true);
                        checkpoint.accept("checked");
                        return "checked";
                    }
                })
                .withPropertyValues("artemis.aiworker.id=document-worker", "artemis.aiworker.image=" + image, "artemis.aiworker.workload=document-check",
                        "artemis.aiworker.profile=plain-text", "spring.ai.model.chat=none", "spring.artemis.broker-url=tcp://broker.invalid:61617?sslEnabled=true",
                        "spring.artemis.user=test", "spring.artemis.password=test")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkerSupervisorService.class);
                    assertThat(context).doesNotHaveBean("openAiChatModel").doesNotHaveBean("hyperionWorkloadService");
                    var supervisor = context.getBean(WorkerSupervisorService.class);
                    supervisor.heartbeat();
                    var heartbeat = events.poll(5, TimeUnit.SECONDS);
                    assertThat(heartbeat).isNotNull();
                    var identity = new ExecutionIdentityDTO("document-job", "document:abc", UUID.randomUUID(), "document-worker", heartbeat.incarnation(), 0);
                    supervisor.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, identity,
                            new ExecutionAssignmentDTO(identity, capability, Instant.now().plusSeconds(30), image, "document")));
                    var observed = new java.util.ArrayList<WorkerEventDTO>();
                    while (observed.stream().noneMatch(event -> event.type() == WorkerEventType.FINISHED)) {
                        var event = events.poll(5, TimeUnit.SECONDS);
                        assertThat(event).isNotNull();
                        observed.add(event);
                    }
                    assertThat(observed.stream().map(WorkerEventDTO::type)).containsSubsequence(WorkerEventType.ACCOUNTING, WorkerEventType.CHECKPOINT, WorkerEventType.FINISHED);
                    assertThat(observed.getLast().payload()).isEqualTo("checked");
                });
    }
}
