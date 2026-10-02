package de.tum.cit.aet.artemis.aiworker.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import de.tum.cit.aet.artemis.aiworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerCommandType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.service.WorkerSupervisorService;
import de.tum.cit.aet.artemis.core.config.TaskSchedulingConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;

class WorkerCommandListenerTest {

    @Test
    void workerOnlySchedulerPollsCommandsWhileHeartbeatIsBlocked() {
        WorkerTransport transport = mock(WorkerTransport.class);
        WorkerSettings settings = mock(WorkerSettings.class);
        WorkerSupervisorService supervisor = mock(WorkerSupervisorService.class);
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        when(settings.id()).thenReturn("worker-1");
        when(provider.isConnectedToCluster()).thenReturn(true);
        when(supervisor.isCurrentIncarnation(any())).thenReturn(true);
        var publishing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var processed = new CountDownLatch(2);
        var commands = new LinkedBlockingQueue<WorkerCommandDTO>();
        doAnswer(_ -> {
            publishing.countDown();
            release.await();
            return null;
        }).when(supervisor).heartbeat();
        doAnswer(invocation -> {
            var command = commands.poll();
            if (command != null) {
                Consumer<WorkerCommandDTO> apply = invocation.getArgument(2);
                apply.accept(command);
                processed.countDown();
            }
            return null;
        }).when(transport).receiveCommands(eq("worker-1"), any(), any());
        new ApplicationContextRunner().withPropertyValues("spring.profiles.active=aiworker").withUserConfiguration(TaskSchedulingConfiguration.class, WorkerCommandListener.class)
                .withBean(WorkerTransport.class, () -> transport).withBean(WorkerSettings.class, () -> settings)
                .withBean("testSupervisor", WorkerSupervisorService.class, () -> supervisor).withBean(DistributedDataProvider.class, () -> provider)
                .withPropertyValues("spring.threads.virtual.enabled=true", "artemis.aiworker.heartbeat-interval=PT0.1S").run(context -> {
                    try {
                        assertThat(context).hasNotFailed();
                        assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
                        var identity = new ExecutionIdentityDTO("job", "token", UUID.randomUUID(), "worker-1", UUID.randomUUID(), 0);
                        var renew = new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.RENEW, identity, null);
                        var cancel = new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.CANCEL, identity, null);
                        commands.add(renew);
                        commands.add(cancel);
                        assertThat(processed.await(5, TimeUnit.SECONDS)).isTrue();
                        verify(supervisor).accept(renew);
                        verify(supervisor).accept(cancel);
                    }
                    finally {
                        release.countDown();
                    }
                });
    }

    @Test
    void waitsForProviderConnectionBeforePollingCommands() {
        WorkerTransport transport = mock(WorkerTransport.class);
        WorkerSettings settings = mock(WorkerSettings.class);
        WorkerSupervisorService supervisor = mock(WorkerSupervisorService.class);
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        when(settings.id()).thenReturn("worker-1");
        WorkerCommandListener listener = new WorkerCommandListener(transport, settings, supervisor, provider);

        listener.receive();
        verifyNoInteractions(transport);

        when(provider.isConnectedToCluster()).thenReturn(true);
        listener.receive();
        verify(transport).receiveCommands(eq("worker-1"), any(), any());
    }
}
