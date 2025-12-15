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
import de.tum.cit.aet.artemis.programming.domain.synchronization.ProgrammingExerciseEditorFileChangeType;
import de.tum.cit.aet.artemis.programming.domain.synchronization.ProgrammingExerciseEditorFileType;
import de.tum.cit.aet.artemis.programming.domain.synchronization.ProgrammingExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.programming.dto.synchronization.ProgrammingExerciseEditorFileSyncDTO;
import de.tum.cit.aet.artemis.programming.dto.synchronization.ProgrammingExerciseEditorSyncEventDTO;

class ProgrammingExerciseEditorSyncServiceTest {

    private WebsocketMessagingService websocketMessagingService;

    private ProgrammingExerciseEditorSyncService synchronizationService;

    @BeforeEach
    void setUp() {
        websocketMessagingService = mock(WebsocketMessagingService.class);
        synchronizationService = new ProgrammingExerciseEditorSyncService(websocketMessagingService);
        when(websocketMessagingService.sendMessage(anyString(), any(Object.class))).thenReturn(CompletableFuture.completedFuture(null));
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
        ProgrammingExerciseEditorFileSyncDTO filePatch = ProgrammingExerciseEditorFileSyncDTO.forRename("old/path.txt", "new/path.txt");
        synchronizationService.broadcastFileChanges(50L, ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY, null, filePatch);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/50/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.filePatches()).hasSize(1);
        ProgrammingExerciseEditorFileSyncDTO patch = sentMessage.filePatches().get(0);
        assertThat(patch.fileName()).isEqualTo("old/path.txt");
        assertThat(patch.changeType()).isEqualTo(ProgrammingExerciseEditorFileChangeType.RENAME);
        assertThat(patch.newFileName()).isEqualTo("new/path.txt");
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
    void getSynchronizationTopicGeneratesCorrectTopic() {
        String topic = ProgrammingExerciseEditorSyncService.getSynchronizationTopic(123L);
        assertThat(topic).isEqualTo("/topic/programming-exercises/123/synchronization");
    }
}
