package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Summary of a course competency or prerequisite created from a standardized competency.
 *
 * @param id                             the id of the imported course competency
 * @param title                          the title
 * @param description                    the optional description
 * @param taxonomy                       the optional taxonomy level
 * @param softDueDate                    the optional target date for reaching mastery
 * @param masteryThreshold               the percentage of progress required for mastery, when set
 * @param optional                       whether the imported course competency is optional
 * @param generatedByAi                  whether it was created from an AI recommendation
 * @param courseId                       the id of the course, when available
 * @param linkedStandardizedCompetencyId the id of the linked standardized competency, when available
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyImportResponseDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id, String title, @Nullable String description,
        @Nullable CompetencyTaxonomy taxonomy, @Nullable ZonedDateTime softDueDate, @Nullable Integer masteryThreshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean optional, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean generatedByAi, @Nullable Long courseId,
        @Nullable Long linkedStandardizedCompetencyId) {

    /**
     * Creates a CompetencyImportResponseDTO from the given Competency
     *
     * @param competency the Competency
     * @return the created CompetencyImportResponseDTO
     */
    public static CompetencyImportResponseDTO of(CourseCompetency competency) {
        Long courseId = competency.getCourse() == null ? null : competency.getCourse().getId();
        Long linkedStandardizedCompetencyId = competency.getLinkedStandardizedCompetency() == null ? null : competency.getLinkedStandardizedCompetency().getId();

        return new CompetencyImportResponseDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getSoftDueDate(),
                competency.getMasteryThreshold(), competency.isOptional(), competency.isGeneratedByAi(), courseId, linkedStandardizedCompetencyId);
    }
}
