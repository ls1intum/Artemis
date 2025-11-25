package de.tum.cit.aet.artemis.programming.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseSynchronizationDTO(@NotNull SynchronizationTarget target, @Nullable Long auxiliaryRepositoryId, @NotNull String clientInstanceId) {

    public enum SynchronizationTarget {
        PROBLEM_STATEMENT, TEMPLATE_REPOSITORY, SOLUTION_REPOSITORY, TESTS_REPOSITORY, AUXILIARY_REPOSITORY
    }
}
