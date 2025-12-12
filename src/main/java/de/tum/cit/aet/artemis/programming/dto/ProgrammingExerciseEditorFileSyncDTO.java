package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseEditorFileSyncDTO(String fileName, @Nullable String patch, @Nullable String changeType, @Nullable String newFileName, @Nullable String fileType) {
}
