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
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseEditorFileSyncDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseEditorSyncEventDTO;

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
    void broadcastChangeSendsPayloadToTopic() {
        synchronizationService.broadcastChange(42L, ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 9L);
        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/42/synchronization"), captor.capture());
        var sentMessage = captor.getValue();
        assertThat(sentMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY);
        assertThat(sentMessage.auxiliaryRepositoryId()).isEqualTo(9L);
        assertThat(sentMessage.filePatches()).isNull();
    }

    @Test
    void broadcastChangeForTemplateRepository() {
        synchronizationService.broadcastChange(100L, ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY, null);
        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/100/synchronization"), captor.capture());
        var sentMessage = captor.getValue();
        assertThat(sentMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY);
        assertThat(sentMessage.auxiliaryRepositoryId()).isNull();
        assertThat(sentMessage.filePatches()).isNull();
    }

    @Test
    void broadcastFileChangesWithCreateOperation() {
        ProgrammingExerciseEditorFileSyncDTO filePatch = new ProgrammingExerciseEditorFileSyncDTO("src/Main.java", null, "CREATE", null, "FILE");
        synchronizationService.broadcastFileChanges(42L, ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY, null, filePatch);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/42/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.SOLUTION_REPOSITORY);
        assertThat(sentMessage.auxiliaryRepositoryId()).isNull();
        assertThat(sentMessage.filePatches()).hasSize(1);

        ProgrammingExerciseEditorFileSyncDTO patch = sentMessage.filePatches().get(0);
        assertThat(patch.fileName()).isEqualTo("src/Main.java");
        assertThat(patch.changeType()).isEqualTo("CREATE");
        assertThat(patch.fileType()).isEqualTo("FILE");
    }

    @Test
    void broadcastFileChangesWithRenameOperation() {
        ProgrammingExerciseEditorFileSyncDTO filePatch = new ProgrammingExerciseEditorFileSyncDTO("old/path.txt", null, "RENAME", "new/path.txt", null);
        synchronizationService.broadcastFileChanges(50L, ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY, null, filePatch);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/50/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.filePatches()).hasSize(1);
        ProgrammingExerciseEditorFileSyncDTO patch = sentMessage.filePatches().get(0);
        assertThat(patch.fileName()).isEqualTo("old/path.txt");
        assertThat(patch.changeType()).isEqualTo("RENAME");
        assertThat(patch.newFileName()).isEqualTo("new/path.txt");
    }

    @Test
    void broadcastFileChangesWithDeleteOperation() {
        ProgrammingExerciseEditorFileSyncDTO filePatch = new ProgrammingExerciseEditorFileSyncDTO("deleted/file.java", null, "DELETE", null, null);
        synchronizationService.broadcastFileChanges(60L, ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 5L, filePatch);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/60/synchronization"), captor.capture());
        var sentMessage = captor.getValue();

        assertThat(sentMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.AUXILIARY_REPOSITORY);
        assertThat(sentMessage.auxiliaryRepositoryId()).isEqualTo(5L);
        assertThat(sentMessage.filePatches()).hasSize(1);
        ProgrammingExerciseEditorFileSyncDTO patch = sentMessage.filePatches().get(0);
        assertThat(patch.fileName()).isEqualTo("deleted/file.java");
        assertThat(patch.changeType()).isEqualTo("DELETE");
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
}
