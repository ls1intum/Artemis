package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static de.tum.cit.aet.artemis.hyperion.web.HyperionWebsocketTopics.ACTIVE_GENERATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpSubscriptionMatcher;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.hyperion.dto.ActiveGenerationDTO;

class GenerationMonitoringWebsocketServiceTest {

    private final GenerationMonitoringService source = mock(GenerationMonitoringService.class);

    private final WebsocketMessagingService messaging = mock(WebsocketMessagingService.class);

    private final SimpUserRegistry subscribers = mock(SimpUserRegistry.class);

    private final GenerationMonitoringWebsocketService service = new GenerationMonitoringWebsocketService(source, messaging, subscribers);

    @Test
    void startsOnTheSchedulingCoreWithoutAnIncomingRequest() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("core", "localci", "localvc", "scheduling");
            TestPropertyValues.of("artemis.hyperion.enabled=true", "artemis.hyperion.exercise-generation.enabled=true").applyTo(context);
            context.registerBean(GenerationMonitoringService.class, () -> source);
            context.registerBean(WebsocketMessagingService.class, () -> messaging);
            context.registerBean(SimpUserRegistry.class, () -> subscribers);
            context.register(GenerationMonitoringWebsocketService.class);
            context.refresh();

            assertThat(context.getBeanFactory().containsSingleton("generationMonitoringWebsocketService")).isTrue();
        }
    }

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
            when(subscription.getDestination()).thenReturn(ACTIVE_GENERATIONS.template());
            assertThat(matcher.match(subscription)).isTrue();
            when(subscription.getDestination()).thenReturn("/topic/other");
            assertThat(matcher.match(subscription)).isFalse();
            return Set.of(subscription);
        });
        var snapshot = List.of(mock(ActiveGenerationDTO.class));
        when(source.activeGenerations()).thenReturn(snapshot);
        service.publish();
        verify(messaging).sendMessage(ACTIVE_GENERATIONS.at(), snapshot);
        when(source.activeGenerations()).thenReturn(List.of());
        service.publish();
        verify(messaging).sendMessage(ACTIVE_GENERATIONS.at(), List.of());
    }
}
