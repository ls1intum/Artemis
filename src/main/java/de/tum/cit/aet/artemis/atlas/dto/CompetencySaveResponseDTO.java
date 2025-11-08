package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for competency save operation response.
 * Used when creating or updating one or multiple competencies.
 * Provides detailed feedback about the operation results.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencySaveResponseDTO(boolean success, int created, int updated, int failed, List<String> errors, String message) {
}
