package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing the competency progress of the whole course.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseCompetencyProgressDTO(long competencyId, long numberOfStudents, long numberOfMasteredStudents, double averageStudentScore) {
}
