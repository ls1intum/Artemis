package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseEditorSyncTarget;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseEditorSyncEventDTO(ProgrammingExerciseEditorSyncTarget target, @Nullable Long auxiliaryRepositoryId, @Nullable String clientInstanceId) {
}
