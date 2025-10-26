package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;

/**
 * DTO for Atlas Agent tool responses representing competency information.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentCompetencyDTO(Long id, String title, String description, String taxonomy, Long courseId) {

    /**
     * Creates an AtlasAgentCompetencyDTO from a Competency entity without courseId.
     *
     * @param competency the competency to convert
     * @return the DTO representation
     */
    public static AtlasAgentCompetencyDTO of(Competency competency) {
        return new AtlasAgentCompetencyDTO(competency.getId(), competency.getTitle(), competency.getDescription(),
                competency.getTaxonomy() != null ? competency.getTaxonomy().toString() : "", null);
    }

    /**
     * Creates an AtlasAgentCompetencyDTO from a Competency entity with courseId.
     *
     * @param competency the competency to convert
     * @param courseId   the course ID
     * @return the DTO representation
     */
    public static AtlasAgentCompetencyDTO of(Competency competency, Long courseId) {
        return new AtlasAgentCompetencyDTO(competency.getId(), competency.getTitle(), competency.getDescription(),
                competency.getTaxonomy() != null ? competency.getTaxonomy().toString() : "", courseId);
    }
}
