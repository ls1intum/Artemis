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
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerCommandListener;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerEventPublisher;
import de.tum.cit.aet.artemis.aiworker.service.sandbox.DockerSandboxService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

/** Boots and executes a second workload without Hyperion beans. */
class WorkerIsolationTest {

    @Test
    void doesNotStartWorkerBeansWithoutTheAiworkerProfile() {
        new ApplicationContextRunner().withUserConfiguration(WorkerMessaging.class, WorkerSandboxConfiguration.class, DockerConfiguration.class, WorkerSupervisorService.class,
                WorkerCommandListener.class, DockerSandboxService.class).run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(WorkerSupervisorService.class).doesNotHaveBean(DockerClient.class);
                });
    }

    @Test
    void executesWithoutHyperionOrModelCredentials() {
        String image = "sha256:" + "a".repeat(64);
        var capability = new WorkloadCapabilityDTO("document-check", 1, "plain-text");
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);
        when(docker.listContainersCmd().withShowAll(true).exec()).thenReturn(List.of());
        when(docker.inspectImageCmd(image).exec().getId()).thenReturn(image);
        new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("aiworker"))
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(WorkerMessaging.class, WorkerSandboxConfiguration.class, DockerConfiguration.class, WorkerSupervisorService.class,
                        WorkerCommandListener.class, DockerSandboxService.class)
                .withBean(DistributedDataProvider.class, LocalDataProviderService::new)
                .withBean("testDocker", DockerClient.class, () -> docker, definition -> definition.setPrimary(true))
                .withBean("testPublisher", WorkerEventPublisher.class, () -> events::add, definition -> definition.setPrimary(true))
                .withBean(WorkloadApi.class, () -> new WorkloadApi() {

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
                }).withPropertyValues("artemis.aiworker.id=document-worker", "artemis.aiworker.image=" + image, "artemis.aiworker.workload=document-check",
                        "artemis.aiworker.profile=plain-text", "spring.ai.model.chat=none")
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
