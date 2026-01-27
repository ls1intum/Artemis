package de.tum.cit.aet.artemis.programming.dto.synchronization;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.synchronization.ProgrammingExerciseEditorAwarenessEventType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseEditorAwarenessDTO(ProgrammingExerciseEditorAwarenessEventType type, @Nullable String update, @Nullable String fileName) {
}
