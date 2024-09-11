package de.tum.cit.aet.artemis.web.rest.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Course;

/**
 * Returned by the for-dashboard resources.
 * Contains a course (e.g. shown in the course-card.component) and different types of scores.
 *
 * @param course               the course
 * @param totalScores          the total scores for the course, including the total max and reachable points and the total scores of the currently logged in student (including
 *                                 total absolute and relative scores).
 * @param textScores           the scores for just the text exercises in the course, including the max and reachable points and the scores of the currently logged in student
 * @param programmingScores    the scores for just the programming exercises in the course, including the max and reachable points and the scores of the currently logged in student
 * @param modelingScores       the scores for just the modeling exercises in the course, including the max and reachable points and the scores of the currently logged in student
 * @param fileUploadScores     the scores for just the file upload exercises in the course, including the max and reachable points and the scores of the currently logged in student
 * @param quizScores           the scores for just the quiz exercises in the course, including the max and reachable points and the scores of the currently logged in student
 * @param participationResults the relevant result for each participation.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForDashboardDTO(Course course, CourseScoresDTO totalScores, CourseScoresDTO textScores, CourseScoresDTO programmingScores, CourseScoresDTO modelingScores,
        CourseScoresDTO fileUploadScores, CourseScoresDTO quizScores, Set<ParticipationResultDTO> participationResults) {
}
