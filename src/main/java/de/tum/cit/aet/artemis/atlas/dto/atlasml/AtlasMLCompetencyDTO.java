package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;

/**
 * DTO for AtlasML API communication representing a competency.
 * This matches the Python AtlasML API structure with single-letter taxonomy codes.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasMLCompetencyDTO(@NotNull Long id, @NotNull String title, @Nullable String description, @JsonProperty("course_id") @NotNull Long courseId) {

    /**
     * Convert from domain Competency to AtlasML DTO.
     */
    @Nullable
    public static AtlasMLCompetencyDTO fromDomain(@Nullable Competency competency) {
        if (competency == null) {
            return null;
        }

        // Get course ID from competency - assuming competency has course information
        @NotNull
        Long courseId = competency.getCourse().getId();

        return new AtlasMLCompetencyDTO(competency.getId(), competency.getTitle(), competency.getDescription(), courseId);
    }

    /**
     * Convert to domain Competency.
     * Note: This creates a basic competency without all the domain-specific fields.
     */
    @NotNull
    public Competency toDomain() {
        return new Competency(title, description, null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, CompetencyTaxonomy.UNDERSTAND, false);
    }
}
