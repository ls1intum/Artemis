package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * DTO for requesting code generation for a programming exercise.
 * Contains the repository type to determine which generation strategy to use.
 * Set initialAutoGeneration to true for the first automatically-triggered end-to-end generation flow.
 * The repository type is required; it is nullable here only so that a missing one is rejected with a proper error.
 * To ask whether a job is already running, use the active-job endpoint instead of this request.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CodeGenerationRequestDTO(@Nullable RepositoryType repositoryType, boolean initialAutoGeneration, @Nullable List<Long> selectedFeedbackThreadIds) {

    public CodeGenerationRequestDTO(@Nullable RepositoryType repositoryType) {
        this(repositoryType, false, null);
    }

    public CodeGenerationRequestDTO(@Nullable RepositoryType repositoryType, boolean initialAutoGeneration) {
        this(repositoryType, initialAutoGeneration, null);
    }
}
