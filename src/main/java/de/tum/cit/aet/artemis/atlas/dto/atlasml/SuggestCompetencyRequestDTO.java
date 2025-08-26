package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for requesting competency suggestions from AtlasML.
 * Maps to the Python SuggestCompetencyRequest model.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SuggestCompetencyRequestDTO(String description, Long courseId) {
}
