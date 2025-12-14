package de.tum.cit.aet.artemis.atlas.dto.atlasAgent;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a structured error for competency operations.
 * Errors are represented by a DTO to provide structured data to LLM tools without throwing exceptions.
 * Used by LLM tools to provide detailed error information that the LLM can translate into natural language.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyErrorDTO(String competencyTitle, @NotNull String errorType, String details) {
}
