package de.tum.cit.aet.artemis.hyperion.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * DTO for requesting code generation for a programming exercise.
 * Contains the repository type to determine which generation strategy to use.
 * Set checkOnly to true to return an existing job without starting a new one.
 * Set initialAutoGeneration to true for the first automatically-triggered end-to-end generation flow.
 * Repository type is optional when checkOnly is true.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CodeGenerationRequestDTO(@Nullable RepositoryType repositoryType, boolean checkOnly, boolean initialAutoGeneration) {

    public CodeGenerationRequestDTO(@Nullable RepositoryType repositoryType, boolean checkOnly) {
        this(repositoryType, checkOnly, false);
    }
}
