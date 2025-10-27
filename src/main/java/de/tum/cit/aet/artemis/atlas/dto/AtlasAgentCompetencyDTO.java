package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;

/**
 * DTO for Atlas Agent tool responses representing competency information.
 * All fields are always included in JSON to ensure consistent LLM responses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentCompetencyDTO(@NotNull Long id, @NotNull String title, @NotNull String description, @NotNull String taxonomy, @Nullable Long courseId) {

    /**
     * Creates an AtlasAgentCompetencyDTO from a Competency entity without courseId.
     *
     * @param competency the competency to convert
     * @return the DTO representation
     */
    public static AtlasAgentCompetencyDTO of(@NotNull Competency competency) {
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
    public static AtlasAgentCompetencyDTO of(@NotNull Competency competency, @NotNull Long courseId) {
        return new AtlasAgentCompetencyDTO(competency.getId(), competency.getTitle(), competency.getDescription(),
                competency.getTaxonomy() != null ? competency.getTaxonomy().toString() : "", courseId);
    }
}
