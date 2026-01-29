package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.synchronization.ProgrammingExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.programming.dto.synchronization.ProgrammingExerciseEditorSyncEventType;
import de.tum.cit.aet.artemis.programming.dto.synchronization.ProgrammingExerciseNewCommitAlertDTO;

class ProgrammingExerciseEditorSyncServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "editorsyncservice";

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Autowired
    private ProgrammingExerciseEditorSyncService synchronizationService;

    @BeforeEach
    void setUp() {
        doReturn(CompletableFuture.completedFuture(null)).when(websocketMessagingService).sendMessage(anyString(), any(Object.class));
        clearInvocations(websocketMessagingService);
    }

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void broadcastNewCommitAlert() {
        synchronizationService.broadcastNewCommitAlert(90L, ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY, null);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseNewCommitAlertDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/90/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY);
        assertThat(sentMessage.eventType()).isEqualTo(ProgrammingExerciseEditorSyncEventType.NEW_COMMIT_ALERT);
        assertThat(sentMessage.auxiliaryRepositoryId()).isNull();
    }

    @Test
    void broadcastNewCommitAlertForAuxiliaryRepository() {
        synchronizationService.broadcastNewCommitAlert(100L, ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 25L);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseNewCommitAlertDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/100/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY);
        assertThat(sentMessage.eventType()).isEqualTo(ProgrammingExerciseEditorSyncEventType.NEW_COMMIT_ALERT);
        assertThat(sentMessage.auxiliaryRepositoryId()).isEqualTo(25L);
    }

    @Test
    void broadcastNewCommitAlertUsesClientInstanceHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ProgrammingExerciseEditorSyncService.CLIENT_INSTANCE_HEADER, "client-commits");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        synchronizationService.broadcastNewCommitAlert(101L, ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY, null);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseNewCommitAlertDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/101/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.clientInstanceId()).isEqualTo("client-commits");
        assertThat(sentMessage.eventType()).isEqualTo(ProgrammingExerciseEditorSyncEventType.NEW_COMMIT_ALERT);
    }

    @Test
    void getSynchronizationTopicGeneratesCorrectTopic() {
        String topic = ProgrammingExerciseEditorSyncService.getSynchronizationTopic(123L);
        assertThat(topic).isEqualTo("/topic/programming-exercises/123/synchronization");
    }

    @Test
    void getClientInstanceIdReturnsNullWhenNoRequest() {
        assertThat(ProgrammingExerciseEditorSyncService.getClientInstanceId()).isNull();
    }

    @Test
    void getClientInstanceIdReturnsNullForNonServletAttributes() {
        RequestAttributes nonServletAttributes = mock(RequestAttributes.class);
        RequestContextHolder.setRequestAttributes(nonServletAttributes);

        assertThat(ProgrammingExerciseEditorSyncService.getClientInstanceId()).isNull();
    }
}
