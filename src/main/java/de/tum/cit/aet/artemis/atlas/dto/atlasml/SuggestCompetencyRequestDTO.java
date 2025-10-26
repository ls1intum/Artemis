package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for requesting competency suggestions from AtlasML.
 * Maps to the Python SuggestCompetencyRequest model.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SuggestCompetencyRequestDTO(@NotNull String description, @JsonProperty("course_id") @NotNull Long courseId) {
}
