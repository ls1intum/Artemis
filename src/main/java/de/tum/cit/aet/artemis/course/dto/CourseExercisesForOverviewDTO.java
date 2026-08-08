package de.tum.cit.aet.artemis.course.dto;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationResultDTO;

/**
 * The exercise data of a course overview: the user's exercises with their participations, submissions and results, plus
 * the derived scores.
 * <p>
 * This is the exercises-tab counterpart of {@link CourseForDashboardDTO}. It deliberately carries no course, lectures,
 * exams or tab counts, so entering a course on any other tab does not pay for loading them.
 *
 * @param exercises                     the course exercises visible to the user, with participations, submissions and results
 * @param totalScores                   the total scores for the course (max and reachable points plus the student's absolute and relative scores)
 * @param textScores                    the scores for just the text exercises
 * @param programmingScores             the scores for just the programming exercises
 * @param modelingScores                the scores for just the modeling exercises
 * @param fileUploadScores              the scores for just the file upload exercises
 * @param quizScores                    the scores for just the quiz exercises
 * @param participationResults          the relevant result for each participation
 * @param achievedPointsPerVariantGroup the points the student earns from each exercise variant group, keyed by group id
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseExercisesForOverviewDTO(
        // Mirrors Course#exercises: without this the exercises are serialised at the top level rather than nested under
        // their course, so each one would drag a full copy of the course along (measured: ~40% slower responses).
        @JsonIgnoreProperties("course") Set<Exercise> exercises, CourseScoresDTO totalScores, CourseScoresDTO textScores, CourseScoresDTO programmingScores,
        CourseScoresDTO modelingScores, CourseScoresDTO fileUploadScores, CourseScoresDTO quizScores, Set<ParticipationResultDTO> participationResults,
        Map<Long, Double> achievedPointsPerVariantGroup) {

    /**
     * Re-envelopes the score calculation result, which is shared with the (deprecated) for-dashboard endpoint, dropping
     * the course so only the exercise data is sent.
     *
     * @param dto the dashboard DTO produced by the shared score calculation
     * @return the exercise-only view of it
     */
    public static CourseExercisesForOverviewDTO from(CourseForDashboardDTO dto) {
        Set<Exercise> exercises = dto.course().getExercises();
        // Every participation here belongs to the requesting user, so the student on it is the user the client already
        // has. Serialising it once per exercise was two thirds of each participation and a third of the whole response.
        exercises.stream().map(Exercise::getStudentParticipations).filter(Objects::nonNull).flatMap(Collection::stream).forEach(StudentParticipation::filterSensitiveInformation);
        return new CourseExercisesForOverviewDTO(exercises, dto.totalScores(), dto.textScores(), dto.programmingScores(), dto.modelingScores(), dto.fileUploadScores(),
                dto.quizScores(), dto.participationResults(), dto.achievedPointsPerVariantGroup());
    }
}
