package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for requesting code generation for a programming exercise.
 * Currently empty but can be extended with generation options in the future.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CodeGenerationRequestDTO() {
}
