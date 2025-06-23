package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for requesting competency suggestions from AtlasML.
 * Maps to the Python SuggestCompetencyRequest model.
 */
public class SuggestCompetencyRequestDTO {

    @JsonProperty("id")
    private String id;

    @JsonProperty("description")
    private String description;

    // Default constructor for Jackson
    public SuggestCompetencyRequestDTO() {
    }

    public SuggestCompetencyRequestDTO(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "SuggestCompetencyRequestDTO{" + "id='" + id + '\'' + ", description='" + description + '\'' + '}';
    }
}
