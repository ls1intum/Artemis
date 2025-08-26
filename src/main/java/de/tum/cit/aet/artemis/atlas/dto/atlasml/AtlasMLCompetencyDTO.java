package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

/**
 * DTO for AtlasML API communication representing a competency.
 * This matches the Python AtlasML API structure with single-letter taxonomy codes.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasMLCompetencyDTO(Long id, String title, String description, @JsonProperty("course_id") Long courseId) {

    /**
     * Convert from domain Competency to AtlasML DTO.
     */
    public static AtlasMLCompetencyDTO fromDomain(Competency competency) {
        if (competency == null) {
            return null;
        }

        // Get course ID from competency - assuming competency has course information
        Long courseId = competency.getCourse() != null ? competency.getCourse().getId() : null;

        return new AtlasMLCompetencyDTO(competency.getId(), competency.getTitle(), competency.getDescription(), courseId);
    }

    /**
     * Convert to domain Competency.
     * Note: This creates a basic competency without all the domain-specific fields.
     */
    public Competency toDomain() {
        return new Competency(title, description, null, null, CompetencyTaxonomy.UNDERSTAND, false);
    }
}
