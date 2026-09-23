package de.tum.cit.aet.artemis.aiworker.service;

import static de.tum.cit.aet.artemis.core.config.Constants.AI_WORKER_MONITORING_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpSubscriptionMatcher;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import de.tum.cit.aet.artemis.aiworker.dto.WorkerStatusDTO;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;

class WorkerMonitoringServiceTest {

    private final WorkerRegistryService source = mock(WorkerRegistryService.class);

    private final WebsocketMessagingService messaging = mock(WebsocketMessagingService.class);

    private final SimpUserRegistry subscribers = mock(SimpUserRegistry.class);

    private final WorkerMonitoringService service = new WorkerMonitoringService(source, messaging, subscribers);

    @Test
    void doesNotReadDistributedStateWithoutSubscribers() {
        service.publish();
        verifyNoInteractions(source, messaging);
    }

    @Test
    void sendsCurrentSnapshotOnlyToTheAdminTopic() {
        var subscription = mock(SimpSubscription.class);
        when(subscribers.findSubscriptions(any())).thenAnswer(invocation -> {
            SimpSubscriptionMatcher matcher = invocation.getArgument(0);
            when(subscription.getDestination()).thenReturn(AI_WORKER_MONITORING_TOPIC);
            assertThat(matcher.match(subscription)).isTrue();
            when(subscription.getDestination()).thenReturn("/topic/other");
            assertThat(matcher.match(subscription)).isFalse();
            return Set.of(subscription);
        });
        var snapshot = List.of(mock(WorkerStatusDTO.class));
        when(source.workerStatuses()).thenReturn(snapshot);
        service.publish();
        verify(messaging).sendMessage(AI_WORKER_MONITORING_TOPIC, snapshot);
        when(source.workerStatuses()).thenReturn(List.of());
        service.publish();
        verify(messaging).sendMessage(AI_WORKER_MONITORING_TOPIC, List.of());
    }
}
