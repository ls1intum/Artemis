package de.tum.cit.aet.artemis.aiworker.service.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.aiworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.aiworker.service.WorkerSupervisorService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;

class WorkerCommandListenerTest {

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
        verify(transport).receiveCommands(org.mockito.ArgumentMatchers.eq("worker-1"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
