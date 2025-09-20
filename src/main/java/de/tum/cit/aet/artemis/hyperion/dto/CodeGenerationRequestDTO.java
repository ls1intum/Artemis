package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * DTO for requesting code generation for a programming exercise.
 * Contains the repository type to determine which generation strategy to use.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CodeGenerationRequestDTO(@NotNull RepositoryType repositoryType) {
}
