package de.tum.cit.aet.artemis.course.dto;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationResultDTO;

/**
 * Returned by the for-dashboard resources.
 * Contains a course (e.g. shown in the course-card.component) and different types of scores.
 *
 * @param course                        the course
 * @param totalScores                   the total scores for the course (max and reachable points plus the student's absolute and relative scores)
 * @param textScores                    the scores for just the text exercises (max and reachable points plus the student's scores)
 * @param programmingScores             the scores for just the programming exercises (max and reachable points plus the student's scores)
 * @param modelingScores                the scores for just the modeling exercises (max and reachable points plus the student's scores)
 * @param fileUploadScores              the scores for just the file upload exercises (max and reachable points plus the student's scores)
 * @param quizScores                    the scores for just the quiz exercises (max and reachable points plus the student's scores)
 * @param participationResults          the relevant result for each participation
 * @param courseNotificationCount       the number of notifications for the course
 * @param irisEnabledInCourse           whether the Iris course chat is enabled for this course
 * @param achievedPointsPerVariantGroup the points the student earns from each exercise variant group, keyed by group id, capped at the group's maxPoints where one is configured
 *                                          and adjusted for plagiarism verdicts. Empty when no variant group contributes. Lets the client show a group's contribution without
 *                                          re-deriving it (which would miss plagiarism deductions).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForDashboardDTO(Course course, CourseScoresDTO totalScores, CourseScoresDTO textScores, CourseScoresDTO programmingScores, CourseScoresDTO modelingScores,
        CourseScoresDTO fileUploadScores, CourseScoresDTO quizScores, Set<ParticipationResultDTO> participationResults, Long courseNotificationCount, Boolean irisEnabledInCourse,
        Map<Long, Double> achievedPointsPerVariantGroup) {

    public CourseForDashboardDTO(Course course, CourseScoresDTO totalScores, CourseScoresDTO textScores, CourseScoresDTO programmingScores, CourseScoresDTO modelingScores,
            CourseScoresDTO fileUploadScores, CourseScoresDTO quizScores, Set<ParticipationResultDTO> participationResults) {
        this(course, totalScores, textScores, programmingScores, modelingScores, fileUploadScores, quizScores, participationResults, 0L, false, Map.of());
    }
}
