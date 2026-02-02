package de.tum.cit.aet.artemis.exercise.service;

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

import de.tum.cit.aet.artemis.exercise.domain.synchronization.ExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseEditorSyncEventType;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseNewCommitAlertDTO;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;

class ExerciseEditorSyncServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "editorsyncservice";

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Autowired
    private ExerciseEditorSyncService synchronizationService;

    /**
     * Stubs websocket messaging to avoid real async behavior during tests.
     */
    @BeforeEach
    void setUp() {
        doReturn(CompletableFuture.completedFuture(null)).when(websocketMessagingService).sendMessage(anyString(), any(Object.class));
        clearInvocations(websocketMessagingService);
    }

    /**
     * Clears request context to avoid cross-test leakage.
     */
    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * Verifies that commit alerts are broadcast with expected payload fields.
     */
    @Test
    void broadcastNewCommitAlert() {
        synchronizationService.broadcastNewCommitAlert(90L, ExerciseEditorSyncTarget.TESTS_REPOSITORY, null);

        var captor = ArgumentCaptor.forClass(ExerciseNewCommitAlertDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/exercises/90/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.target()).isEqualTo(ExerciseEditorSyncTarget.TESTS_REPOSITORY);
        assertThat(sentMessage.eventType()).isEqualTo(ExerciseEditorSyncEventType.NEW_COMMIT_ALERT);
        assertThat(sentMessage.auxiliaryRepositoryId()).isNull();
    }

    /**
     * Verifies that auxiliary repository commit alerts include the repository id.
     */
    @Test
    void broadcastNewCommitAlertForAuxiliaryRepository() {
        synchronizationService.broadcastNewCommitAlert(100L, ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 25L);

        var captor = ArgumentCaptor.forClass(ExerciseNewCommitAlertDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/exercises/100/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.target()).isEqualTo(ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY);
        assertThat(sentMessage.eventType()).isEqualTo(ExerciseEditorSyncEventType.NEW_COMMIT_ALERT);
        assertThat(sentMessage.auxiliaryRepositoryId()).isEqualTo(25L);
    }

    /**
     * Verifies that the client session header is forwarded into the alert payload.
     */
    @Test
    void broadcastNewCommitAlertUsesClientSessionHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ExerciseEditorSyncService.CLIENT_SESSION_HEADER, "client-commits");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        synchronizationService.broadcastNewCommitAlert(101L, ExerciseEditorSyncTarget.SOLUTION_REPOSITORY, null);

        var captor = ArgumentCaptor.forClass(ExerciseNewCommitAlertDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/exercises/101/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.sessionId()).isEqualTo("client-commits");
        assertThat(sentMessage.eventType()).isEqualTo(ExerciseEditorSyncEventType.NEW_COMMIT_ALERT);
    }

    /**
     * Verifies that synchronization topics are generated consistently.
     */
    @Test
    void getSynchronizationTopicGeneratesCorrectTopic() {
        String topic = ExerciseEditorSyncService.getSynchronizationTopic(123L);
        assertThat(topic).isEqualTo("/topic/exercises/123/synchronization");
    }

    /**
     * Verifies that the client session id is null without a request context.
     */
    @Test
    void getClientSessionIdReturnsNullWhenNoRequest() {
        assertThat(ExerciseEditorSyncService.getClientSessionId()).isNull();
    }

    /**
     * Verifies that non-servlet request attributes yield no client session id.
     */
    @Test
    void getClientSessionIdReturnsNullForNonServletAttributes() {
        RequestAttributes nonServletAttributes = mock(RequestAttributes.class);
        RequestContextHolder.setRequestAttributes(nonServletAttributes);

        assertThat(ExerciseEditorSyncService.getClientSessionId()).isNull();
    }
}
