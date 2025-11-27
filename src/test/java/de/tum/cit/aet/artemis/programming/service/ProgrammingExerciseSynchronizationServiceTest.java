package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.programming.domain.SynchronizationTarget;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseSynchronizationDTO;

class ProgrammingExerciseSynchronizationServiceTest {

    private WebsocketMessagingService websocketMessagingService;

    private ProgrammingExerciseSynchronizationService synchronizationService;

    @BeforeEach
    void setUp() {
        websocketMessagingService = mock(WebsocketMessagingService.class);
        synchronizationService = new ProgrammingExerciseSynchronizationService(websocketMessagingService);
        when(websocketMessagingService.sendMessage(anyString(), any(Object.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void broadcastChangeSendsPayloadToTopic() {
        synchronizationService.broadcastChange(42L, SynchronizationTarget.AUXILIARY_REPOSITORY, 9L);
        var captor = ArgumentCaptor.forClass(ProgrammingExerciseSynchronizationDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/42/synchronization"), captor.capture());
        var sentMessage = captor.getValue();
        assertThat(sentMessage.target()).isEqualTo(SynchronizationTarget.AUXILIARY_REPOSITORY);
        assertThat(sentMessage.auxiliaryRepositoryId()).isEqualTo(9L);
    }
}
