package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * DTO for requesting code generation for a programming exercise.
 * Contains the repository type to determine which generation strategy to use.
 * Set checkOnly to true to return an existing job without starting a new one.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CodeGenerationRequestDTO(@NotNull RepositoryType repositoryType, boolean checkOnly) {
}
