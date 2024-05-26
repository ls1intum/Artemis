package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.Prerequisite;

/**
 * DTO used to send responses regarding {@link Prerequisite} objects.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PrerequisiteResponseDTO(long id, String title, String description, CompetencyTaxonomy taxonomy, ZonedDateTime softDueDate, int masteryThreshold, boolean optional,
        SourceCourseDTO sourceCourseDTO) {

    public static PrerequisiteResponseDTO of(Prerequisite prerequisite) {
        SourceCourseDTO sourceCourseDTO = null;
        if (prerequisite.getLinkedCourseCompetency() != null && prerequisite.getLinkedCourseCompetency().getCourse() != null) {
            var course = prerequisite.getLinkedCourseCompetency().getCourse();
            sourceCourseDTO = new SourceCourseDTO(course.getId(), course.getTitle());
        }

        return new PrerequisiteResponseDTO(prerequisite.getId(), prerequisite.getTitle(), prerequisite.getDescription(), prerequisite.getTaxonomy(), prerequisite.getSoftDueDate(),
                prerequisite.getMasteryThreshold(), prerequisite.isOptional(), sourceCourseDTO);
    }

    private record SourceCourseDTO(long id, String title) {

    }
}
