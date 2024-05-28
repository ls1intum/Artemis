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
        LinkedCourseCompetencyDTO linkedCourseCompetencyDTO) {

    /**
     * Converts a Prerequisite to a PrerequisiteResponseDTO (and its linked CourseCompetency to a LinkedCourseCompetencyDTO)
     *
     * @param prerequisite the Prerequisite to convert
     * @return the PrerequisiteResponseDTO
     */
    public static PrerequisiteResponseDTO of(Prerequisite prerequisite) {
        LinkedCourseCompetencyDTO linkedCourseCompetencyDTO = null;
        var linkedCompetency = prerequisite.getLinkedCourseCompetency();
        if (linkedCompetency != null && linkedCompetency.getCourse() != null) {
            var course = linkedCompetency.getCourse();
            linkedCourseCompetencyDTO = new LinkedCourseCompetencyDTO(linkedCompetency.getId(), course.getId(), course.getTitle(), course.getSemester());
        }

        return new PrerequisiteResponseDTO(prerequisite.getId(), prerequisite.getTitle(), prerequisite.getDescription(), prerequisite.getTaxonomy(), prerequisite.getSoftDueDate(),
                prerequisite.getMasteryThreshold(), prerequisite.isOptional(), linkedCourseCompetencyDTO);
    }

    private record LinkedCourseCompetencyDTO(long id, long courseId, String courseTitle, String semester) {

    }
}
