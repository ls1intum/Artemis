package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing the learning goal progress of the whole course.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseLearningGoalProgressDTO(long learningGoalId, long numberOfStudents, long numberOfMasteredStudents, double averageStudentScore) {
}
