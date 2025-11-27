package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.SynchronizationTarget;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseSynchronizationDTO(SynchronizationTarget target, @Nullable Long auxiliaryRepositoryId, @Nullable String clientInstanceId) {
}
