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
import de.tum.cit.aet.artemis.programming.domain.synchronization.ProgrammingExerciseEditorFileChangeType;
import de.tum.cit.aet.artemis.programming.domain.synchronization.ProgrammingExerciseEditorFileType;
import de.tum.cit.aet.artemis.programming.domain.synchronization.ProgrammingExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.programming.dto.synchronization.ProgrammingExerciseEditorFileSyncDTO;
import de.tum.cit.aet.artemis.programming.dto.synchronization.ProgrammingExerciseEditorSyncEventDTO;

class ProgrammingExerciseEditorSyncServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "programmingexerciseeditorsyncservicetest";

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
    void broadcastFileChangesWithCreateOperation() {
        ProgrammingExerciseEditorFileSyncDTO filePatch = ProgrammingExerciseEditorFileSyncDTO.forFileCreate("src/Main.java");
        synchronizationService.broadcastFileChanges(42L, ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY, null, filePatch);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/42/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY);
        assertThat(sentMessage.auxiliaryRepositoryId()).isNull();
        assertThat(sentMessage.filePatches()).hasSize(1);

        ProgrammingExerciseEditorFileSyncDTO patch = sentMessage.filePatches().get(0);
        assertThat(patch.fileName()).isEqualTo("src/Main.java");
        assertThat(patch.changeType()).isEqualTo(ProgrammingExerciseEditorFileChangeType.CREATE);
        assertThat(patch.fileType()).isEqualTo(ProgrammingExerciseEditorFileType.FILE);
    }

    @Test
    void broadcastFileChangesWithRenameOperation() {
        ProgrammingExerciseEditorFileSyncDTO filePatch = ProgrammingExerciseEditorFileSyncDTO.forRename("old/path.txt", "new/path.txt", ProgrammingExerciseEditorFileType.FILE);
        synchronizationService.broadcastFileChanges(50L, ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY, null, filePatch);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/50/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.filePatches()).hasSize(1);
        ProgrammingExerciseEditorFileSyncDTO patch = sentMessage.filePatches().get(0);
        assertThat(patch.fileName()).isEqualTo("old/path.txt");
        assertThat(patch.changeType()).isEqualTo(ProgrammingExerciseEditorFileChangeType.RENAME);
        assertThat(patch.newFileName()).isEqualTo("new/path.txt");
        assertThat(patch.fileType()).isEqualTo(ProgrammingExerciseEditorFileType.FILE);
    }

    @Test
    void broadcastFileChangesWithDeleteOperation() {
        ProgrammingExerciseEditorFileSyncDTO filePatch = ProgrammingExerciseEditorFileSyncDTO.forDelete("deleted/file.java");
        synchronizationService.broadcastFileChanges(60L, ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 5L, filePatch);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/60/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY);
        assertThat(sentMessage.auxiliaryRepositoryId()).isEqualTo(5L);
        assertThat(sentMessage.filePatches()).hasSize(1);
        ProgrammingExerciseEditorFileSyncDTO patch = sentMessage.filePatches().get(0);
        assertThat(patch.fileName()).isEqualTo("deleted/file.java");
        assertThat(patch.changeType()).isEqualTo(ProgrammingExerciseEditorFileChangeType.DELETE);
    }

    @Test
    void broadcastFileChangesWithNullPatch() {
        synchronizationService.broadcastFileChanges(70L, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, null, null);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/70/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY);
        assertThat(sentMessage.filePatches()).isNull();
    }

    @Test
    void broadcastFileChangesWithFolderCreate() {
        ProgrammingExerciseEditorFileSyncDTO filePatch = ProgrammingExerciseEditorFileSyncDTO.forFolderCreate("src/main/java");
        synchronizationService.broadcastFileChanges(80L, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, null, filePatch);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/80/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.filePatches()).hasSize(1);
        ProgrammingExerciseEditorFileSyncDTO patch = sentMessage.filePatches().get(0);
        assertThat(patch.fileName()).isEqualTo("src/main/java");
        assertThat(patch.changeType()).isEqualTo(ProgrammingExerciseEditorFileChangeType.CREATE);
        assertThat(patch.fileType()).isEqualTo(ProgrammingExerciseEditorFileType.FOLDER);
    }

    @Test
    void broadcastNewCommitAlert() {
        synchronizationService.broadcastNewCommitAlert(90L, ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY, null);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/90/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY);
        assertThat(sentMessage.newCommitAlert()).isTrue();
        assertThat(sentMessage.auxiliaryRepositoryId()).isNull();
        assertThat(sentMessage.filePatches()).isNull();
    }

    @Test
    void broadcastNewCommitAlertForAuxiliaryRepository() {
        synchronizationService.broadcastNewCommitAlert(100L, ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 25L);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/100/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY);
        assertThat(sentMessage.newCommitAlert()).isTrue();
        assertThat(sentMessage.auxiliaryRepositoryId()).isEqualTo(25L);
    }

    @Test
    void broadcastNewCommitAlertUsesClientInstanceHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ProgrammingExerciseEditorSyncService.CLIENT_INSTANCE_HEADER, "client-commits");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        synchronizationService.broadcastNewCommitAlert(101L, ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY, null);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/101/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.clientInstanceId()).isEqualTo("client-commits");
        assertThat(sentMessage.newCommitAlert()).isTrue();
    }

    @Test
    void getSynchronizationTopicGeneratesCorrectTopic() {
        String topic = ProgrammingExerciseEditorSyncService.getSynchronizationTopic(123L);
        assertThat(topic).isEqualTo("/topic/programming-exercises/123/synchronization");
    }

    @Test
    void broadcastUsesClientInstanceHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ProgrammingExerciseEditorSyncService.CLIENT_INSTANCE_HEADER, "client-42");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        synchronizationService.broadcastFileChanges(11L, ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT, null, null);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/11/synchronization"), captor.capture());
        assertThat(captor.getValue().clientInstanceId()).isEqualTo("client-42");
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
