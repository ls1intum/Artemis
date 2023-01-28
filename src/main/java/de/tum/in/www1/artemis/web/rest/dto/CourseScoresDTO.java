package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Contains the scores that enable the client to show statistics for a calling student.
 * This includes the percentage of points reached for a course in the course-card.component and the statistics shown in the course-statistics.component.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseScoresDTO(double maxPoints, double reachablePoints, StudentScoresDTO studentScores) {
}
