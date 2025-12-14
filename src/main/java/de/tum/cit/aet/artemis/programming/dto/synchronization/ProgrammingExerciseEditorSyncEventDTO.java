package de.tum.cit.aet.artemis.programming.dto.synchronization;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseEditorSyncTarget;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseEditorSyncEventDTO(ProgrammingExerciseEditorSyncTarget target, @Nullable Long auxiliaryRepositoryId, @Nullable String clientInstanceId,
        @Nullable List<ProgrammingExerciseEditorFileSyncDTO> filePatches, @Nullable Boolean newCommitAlert) {

    public static ProgrammingExerciseEditorSyncEventDTO forFilePatch(ProgrammingExerciseEditorSyncTarget target, @Nullable Long auxiliaryRepositoryId,
            @Nullable String clientInstanceId, @Nullable List<ProgrammingExerciseEditorFileSyncDTO> filePatches) {
        return new ProgrammingExerciseEditorSyncEventDTO(target, auxiliaryRepositoryId, clientInstanceId, filePatches, null);
    }

    public static ProgrammingExerciseEditorSyncEventDTO forGeneralUpdate(ProgrammingExerciseEditorSyncTarget target, @Nullable Long auxiliaryRepositoryId,
            @Nullable String clientInstanceId) {
        return new ProgrammingExerciseEditorSyncEventDTO(target, auxiliaryRepositoryId, clientInstanceId, null, null);
    }

    public static ProgrammingExerciseEditorSyncEventDTO forNewCommitAlert(ProgrammingExerciseEditorSyncTarget target, @Nullable Long auxiliaryRepositoryId,
            @Nullable String clientInstanceId) {
        return new ProgrammingExerciseEditorSyncEventDTO(target, auxiliaryRepositoryId, clientInstanceId, null, true);
    }
}
