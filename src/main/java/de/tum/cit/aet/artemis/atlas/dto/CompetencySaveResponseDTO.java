package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for competency save operation response returned to LLM tools.
 * The LLM interprets this structured data and translates it into natural language for the user.
 * Supports partial success scenarios where some competencies succeed and others fail.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencySaveResponseDTO(int created, int updated, int failed, List<CompetencyErrorDTO> errors) {
}
