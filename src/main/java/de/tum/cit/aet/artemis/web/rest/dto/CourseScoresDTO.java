package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.web.rest.dto.score.StudentScoresDTO;

/**
 * Contains the scores that enable the client to show statistics for a calling student.
 * This includes the percentage of points reached for a course in the course-card.component and the statistics shown in the course-statistics.component.
 *
 * @param maxPoints       the max points achievable (points for all exercises summed up that are included into the score calculation).
 * @param reachablePoints the max points reachable (points for all exercises summed up that are included into the score calculation and whose assessment is done).
 * @param studentScores   the scores of the currently logged in student (including total absolute and relative scores).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseScoresDTO(double maxPoints, double reachablePoints, double reachablePresentationPoints, StudentScoresDTO studentScores) {
}
