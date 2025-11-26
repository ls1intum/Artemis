package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a structured error for competency operations.
 * Used by LLM tools to provide detailed error information that the LLM can translate into natural language.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyError(@Nullable String competencyTitle, String errorType, @Nullable String details) {
}
