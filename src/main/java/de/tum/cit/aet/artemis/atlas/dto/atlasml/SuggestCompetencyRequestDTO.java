package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for requesting competency suggestions from AtlasML.
 * Maps to the Python SuggestCompetencyRequest model.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SuggestCompetencyRequestDTO(@JsonProperty("id") String id, @JsonProperty("description") String description) {
}
